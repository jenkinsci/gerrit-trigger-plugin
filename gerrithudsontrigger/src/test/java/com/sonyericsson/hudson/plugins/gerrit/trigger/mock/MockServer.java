/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger.mock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;

public class MockServer extends Thread {

	public static long WAIT_TIME = 1000;

	ServerSocket server;
	String actual;
	List<InputOutput> inputsOutputs;

	private MockServer(int port) throws IOException {
		server = new ServerSocket(port);
		inputsOutputs = new ArrayList<InputOutput>();
	}

	public static MockServer create() {
		return create(0);
	}

	public static MockServer create(int port) {
		MockServer s = null;
		try {
			s = new MockServer(port);
			s.start();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return s;
	}

	public MockServer expectHTTPGet(String pathAndQuery) {
		return expect("GET " + pathAndQuery + " HTTP/1.1");
	}

	public MockServer send(String data) {
		inputsOutputs.add(new SendData(data + "\r\n"));
		return this;
	}

	public MockServer sendHTTP(String data) {
		// TODO Auto-generated method stub
		return send("HTTP/1.1 200 OK\r\nContent-Length: " + data.length()
				+ "\r\n\r\n" + data);
	}

	public MockServer expectHTTPPost(String path, String postData) {
		return expect("POST " + path + " HTTP/1.1").expect(postData);
	}

	public MockServer expect(String data) {
		inputsOutputs.add(new ExpectData(data));
		return this;
	}

	public synchronized void verify() {
		if (inputsOutputs.size() == 0) {
			if (actual != null)
				Assert.fail("Unexpected call " + actual);
		} else {
			if (actual == null) {
				try {
					wait(WAIT_TIME);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			for (InputOutput exp : inputsOutputs) {
				exp.verify(this, true);

			}
		}
		try {
			server.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void run() {
		try {
			Socket client = server.accept();
			InputStream input = client.getInputStream();
			OutputStream output = client.getOutputStream();

			InputReaderThread reader = new InputReaderThread(input);
			reader.start();

			for (InputOutput inpOutp : inputsOutputs) {
				inpOutp.process(output, this);
			}
			input.close();
			output.close();

		} catch (Exception e) {
			// e.printStackTrace();
		}
	}

	class InputReaderThread extends Thread {
		InputStream input;
		byte[] buf = new byte[4096];

		public InputReaderThread(InputStream input) {
			this.input = input;
		}

		public void run() {
			try {
				int len = 0;
				do {
					len = input.read(buf);
					if (len > 0)
						addActual(new String(buf, 0, len));
				} while (len > 0);

			} catch (IOException e) {
			}

		}

	}

	private synchronized void addActual(String readLine) {
		System.out.println("Received " + readLine);
		if (actual == null)
			actual = readLine;
		else
			actual += readLine;
		notifyAll();

	}

	public int getPort() {
		// TODO Auto-generated method stub
		return server.getLocalPort();
	}

	public synchronized boolean verifyData(String data, boolean fail) {
		long waitUntil = System.currentTimeMillis() + MockServer.WAIT_TIME;
		long waitTime = MockServer.WAIT_TIME;
		while (waitTime > 0 && !verify(actual, data)) {
			try {
				wait(waitTime);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			waitTime = waitUntil - System.currentTimeMillis();
		}
		if (!verify(actual, data)) {
			if (fail)
				Assert.assertEquals(data, actual);
			else
				return false;
		}
		return true;
	}

	private boolean verify(String actualData, String data) {
		if (actualData == null)
			return false;
		actualData = actualData.toLowerCase().replace(" ", "");
		data = data.toLowerCase().replace(" ", "");
		return actualData.indexOf(data) != -1;
	}

}

abstract class InputOutput {

	String data;

	public InputOutput(String data) {
		this.data = data;
	}

	public abstract boolean process(OutputStream output, MockServer server);

	public abstract boolean verify(MockServer server, boolean fail);
}

class ExpectData extends InputOutput {

	public ExpectData(String data) {
		super(data);
	}

	@Override
	public boolean verify(MockServer server, boolean fail) {
		return server.verifyData(data, fail);
	}

	@Override
	public boolean process(OutputStream output, MockServer server) {
		System.out.println("Waiting for " + data);
		return verify(server, false);
	}

}

class SendData extends InputOutput {

	public SendData(String data) {
		super(data);
	}

	@Override
	public boolean verify(MockServer server, boolean fail) {
		return true;
	}

	@Override
	public boolean process(OutputStream output, MockServer server) {
		System.out.println("Sending " + data);
		try {
			output.write(data.getBytes());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return true;
	}

}
