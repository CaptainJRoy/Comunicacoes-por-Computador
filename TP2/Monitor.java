import java.io.*;
import java.net.*;
import java.util.*;

class Monitor
{
	public static void main(String args[]) throws Exception
	{
		DatagramSocket socket = new DatagramSocket(5555);
		InetAddress ipAddress = InetAddress.getByName(args[0]); //ReverseProxy
		Thread registo = new Thread(new Registo(ipAddress, socket));
		Thread pooling = new Thread(new Pooling(ipAddress, socket));
		registo.start();
		pooling.start();
		Thread tcp = new Thread(new MonitorTCP());
		tcp.start();
	}
}

class MonitorTCP implements Runnable{

	public void run(){
		try{
			ServerSocket servidor = new ServerSocket(80);
			while(true){
				Socket cliente = servidor.accept();
				Thread t = new Thread(new Cliente(cliente));
				t.start();
			}
		}
		catch(IOException e){} 

	}
}

class Cliente implements Runnable{

	Socket cliente;

	Cliente(Socket cliente){
		this.cliente = cliente;
	}

	public void run(){
		try{
			BufferedReader fromCliente = new BufferedReader(new InputStreamReader(cliente.getInputStream()));
			PrintWriter toCliente = new PrintWriter(cliente.getOutputStream());
			while(true){
				String s = fromCliente.readLine();
				if (s == null)
					break;
				
				toCliente.println(s.toUpperCase());
				toCliente.flush();
			}
			cliente.close();
		}
		catch(IOException e){
			e.printStackTrace();
		}
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
			catch (IOException | InterruptedException e){
				e.printStackTrace();
			}
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
					enviarInformacao(pdu[1], pdu[2]);
				}

				//System.out.println("RECEIVED: " + sentence);
			}
		}
		catch (IOException e){
			e.printStackTrace();
		}
	}

	void enviarInformacao(String id, String tempoE){
		try{
			//long tempoInicial = (new Date()).getTime();
			long tempoInicial = System.currentTimeMillis();
			byte[] sendData = new byte[1024];

			Process proc = Runtime.getRuntime().exec(new String[] {"bash", "-c", "netstat -an | grep tcp | wc -l"});
			BufferedReader br = new BufferedReader (new InputStreamReader (proc.getInputStream()));

			long tempoEnvio = Long.parseLong(tempoE);
			//long tempoFinal = (new Date()).getTime();
			long tempoFinal = System.currentTimeMillis();
			String sentence = "resposta " + id + " " + br.readLine() + " " + Long.toString(tempoFinal - tempoInicial + tempoEnvio);
			sendData = sentence.getBytes();
			DatagramPacket packet = new DatagramPacket(sendData, sendData.length, ipAddress, 5555);
			socket.send(packet);
		}
		catch (IOException e){
			e.printStackTrace();
		}
	}
}
