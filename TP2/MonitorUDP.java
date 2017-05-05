import java.io.*;
import java.net.*;

class MonitorUDP
{
	public static void main(String args[]) throws Exception
	{
		DatagramSocket socket = new DatagramSocket(5555);
		InetAddress ipAddress = InetAddress.getByName(args[0]);
		Thread registo = new Thread(new Registo(ipAddress, socket));
		Thread pooling = new Thread(new Pooling(ipAddress, socket));
		registo.start();
		pooling.start();
	}
}

class Registo implements Runnable{
	DatagramPacket packet;
	DatagramSocket socket;

	Registo (InetAddress ipAddress, DatagramSocket socket) throws SocketException{
		this.socket = socket;
		byte[] sendData = new byte[1024];
		String sentence = "registo";
		sendData = sentence.getBytes();
		packet = new DatagramPacket (sendData, sendData.length, ipAddress, 5555);
	}

	public void run(){
		while(true){
			try{
				socket.send(packet);
				Thread.sleep(5000);
			}
			catch (IOException | InterruptedException e){}
		}
	}
}

class Pooling implements Runnable{

	DatagramSocket socket;
	InetAddress ipAddress;

	Pooling (InetAddress ipAddress, DatagramSocket socket){
		this.socket = socket;
		this.ipAddress = ipAddress;
	}

	public void run(){
		try{
			byte[] receiveData = new byte[1024];
			DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
			String sentence;
			while(true){
				socket.receive(receivePacket);
				sentence = new String(receivePacket.getData(), 0, receivePacket.getLength());

				String[] pdu = sentence.split(" ");

				if (pdu[0].equals("pedido")){
					enviarInformacao(pdu[1]);
				}

				System.out.println("RECEIVED: " + sentence);
			}
		}
		catch (IOException e){}
	}

	void enviarInformacao(String id){
		try{
			byte[] sendData = new byte[1024];
			String sentence = "resposta " + id;
			sendData = sentence.getBytes();
			DatagramPacket packet = new DatagramPacket(sendData, sendData.length, ipAddress, 5555);
			socket.send(packet);
		}
		catch (IOException e){}
	}
}
