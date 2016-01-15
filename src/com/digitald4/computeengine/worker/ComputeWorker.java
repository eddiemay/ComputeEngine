package com.digitald4.computeengine.worker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.script.ScriptEngineManager;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.json.JSONException;
import org.json.JSONObject;

public class ComputeWorker extends Thread {
	private final ScriptEngineManager engineManager = new ScriptEngineManager();
	private final ScriptEngine engine = engineManager.getEngineByName("nashorn");
	private final String serverUrl;
	private final Socket socket;
	private final BufferedReader reader;
	private final PrintWriter writer;
	public ComputeWorker(String serverUrl) throws UnknownHostException, IOException {
		this.serverUrl = serverUrl;
		socket = new Socket(serverUrl, 6200);
		reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		writer = new PrintWriter(socket.getOutputStream(), true);
	}
	
	public void run() {
		System.out.println("Worker started connected to " + serverUrl);
		try {
			String line;
			while ((line = reader.readLine()) != null && !line.equals("shutdown")) {
				JSONObject jsonOut = new JSONObject();
				try {
					JSONObject json = new JSONObject(line);
					int requestId = json.getInt("requestId");
					jsonOut.put("requestId", requestId);
					String function = json.getString("function");
					Object value = json.get("value");
					System.out.println(function);
					String execute = "execute(" + value + ")";
					System.out.println(execute);
					engine.eval(function);
					jsonOut.put("result", engine.eval(execute));
				} catch (ScriptException | JSONException e) {
					e.printStackTrace();
					jsonOut.put("error",
							new JSONObject().put("message", e.getMessage()).put("stackTrace", e.getStackTrace()));
				} finally {
					System.out.println("return: " + jsonOut);
					writer.println(jsonOut);
				}
			}
		} catch (IOException | JSONException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) throws UnknownHostException, IOException {
		for (int x = 0; x < Runtime.getRuntime().availableProcessors() + 1; x++) {
			new ComputeWorker(args[0]).start();
		}
	}
}
