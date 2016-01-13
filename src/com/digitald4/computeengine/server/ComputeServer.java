package com.digitald4.computeengine.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JOptionPane;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ComputeServer {

	private ServerSocket workerSocket;
	private ServerSocket requestSocket;
	private List<Worker> workers = new ArrayList<Worker>();
	private Map<Integer, Requester> requests = new HashMap<Integer, Requester>();
	private boolean run = true;
	
	public ComputeServer() {
		try {
			workerSocket = new ServerSocket(6200, 100);
			requestSocket = new ServerSocket(6210, 10);
			new WorkerConnector().start();
			new RequestConnector().start();
			System.out.println("Server running");
		} catch (Exception e) {
			shutdown();
			e.printStackTrace();
		}
	}
	
	public void shutdown() {
		run = false;
		try {
			if (workerSocket != null) {
				workerSocket.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			if (requestSocket != null) {
				requestSocket.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private class WorkerConnector extends Thread {
		public void run() {
			while (run) {
				try {
					Worker worker = new Worker(workerSocket.accept());
					worker.start();
					workers.add(worker);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private class RequestConnector extends Thread {
		public void run() {
			int id = 1000;
			while (run) {
				try {
					Requester requester = new Requester(id++, requestSocket.accept());
					requests.put(requester.getRequestId(), requester);
					requester.start();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private class Worker extends Thread {
		private final Socket socket;
		private final BufferedReader reader;
		private final PrintWriter writer;
		
		public Worker(Socket socket) throws IOException {
			this.socket = socket;
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			writer = new PrintWriter(socket.getOutputStream(), true);
		}
		
		public void execute(Requester requester) {
			try {
				Object nextValue = requester.nextValue();
				if (nextValue != null) {
					writer.println(new JSONObject()
							.put("requestId", requester.getRequestId())
							.put("function", requester.getCommand())
							.put("value", nextValue));
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		
		public void run() {
			String line;
			try {
				while ((line = reader.readLine()) != null) {
					JSONObject json = new JSONObject(line);
					int requestId = json.getInt("requestId");
					Requester requester = requests.get(requestId);
					if (requester != null) {
						requester.addResult(json);
						Object nextValue = requester.nextValue();
						if (nextValue != null) {
							writer.println(new JSONObject()
									.put("requestId", requestId)
									.put("function", requester.getCommand())
									.put("value", nextValue));
						}
					}
				}
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (JSONException e) {
				e.printStackTrace();
			} finally {
				workers.remove(this);
			}
		}
	}
	
	private class Requester extends Thread {
		private final int id;
		private final Socket socket;
		private final BufferedReader reader;
		private final PrintWriter writer;
		private AtomicInteger atomicInt = new AtomicInteger();
		private JSONArray values;
		private String command;
		private JSONArray results = new JSONArray();
		private JSONArray errors = new JSONArray();
		
		public Requester(int id, Socket socket) throws IOException {
			this.id = id;
			this.socket = socket;
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			writer = new PrintWriter(socket.getOutputStream(), true);
		}
		
		public void run() {
			try {
				JSONObject json = new JSONObject(reader.readLine());
				values = json.getJSONArray("values");
				command = json.getString("function");
				System.out.println("Command: " + command);
				System.out.println("Values: " + values);
				writer.println(new JSONObject()
						.put("status", "Running job with " + workers.size() + " workers"));
				for (Worker worker : workers) {
					worker.execute(this);
				}
			} catch (IOException | JSONException e) {
				e.printStackTrace();
			}
		}
		
		public int getRequestId() {
			return id;
		}
		
		public String getCommand() {
			return command;
		}
		
		public Object nextValue() {
			int index = atomicInt.getAndIncrement();
			if (index < values.length()) {
				try {
					return values.get(index);
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
			return null;
		}
		
		public void addResult(JSONObject json) throws JSONException {
			if (json.has("result")) {
				Object result = json.get("result");
				System.out.println("Got result: " + result);
				results.put(result);
			} else if (json.has("error")) {
				JSONObject error = json.getJSONObject("error");
				System.out.println("Got error: " + error);
				errors.put(error);
			}
			if (results.length() + errors.length() == values.length()) {
				try (Socket socket = this.socket;) {
					writer.println(new JSONObject()
							.put("results", results)
							.put("errors", errors));
					requests.remove(getRequestId());
				} catch (IOException | JSONException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public static void main(String[] args) {
		ComputeServer server = new ComputeServer();
		JOptionPane.showMessageDialog(null, "Server running. Close to stop");
		server.shutdown();
		System.exit(0);
	}
}
