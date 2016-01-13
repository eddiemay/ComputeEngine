package com.digitald4.computeengine.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ComputeClient {
	private final Socket socket;
	private final BufferedReader reader;
	private final PrintWriter writer;
	
	public ComputeClient() throws UnknownHostException, IOException {
		socket = new Socket("localhost", 6210);
		reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		writer = new PrintWriter(socket.getOutputStream(), true);
	}
	
	public JSONArray compute(JSONArray values, String javascript) {
		JSONArray results = null;
		try {
			JSONObject json = new JSONObject().put("values", values).put("function", javascript);
			System.out.println(json);
			writer.println(json);
			System.out.println("Status: " + reader.readLine());
			JSONObject ret = new JSONObject(reader.readLine());
			results = ret.getJSONArray("results");
			System.out.println("Results: " + results);
			System.out.println("Errors: " + ret.getJSONArray("errors"));
		} catch (IOException | JSONException e) {
			e.printStackTrace();
		}
		return results;
	}
	
	public static void main(String[] args) throws UnknownHostException, IOException {
		new ComputeClient().compute(
				new JSONArray().put("'eddie'").put("'elephant'").put("'mac'").put("'larry'").put("'computer'").put("'lemon'"),
				"execute = function(str) { return str.toUpperCase(); }");
	}
}
