import java.io.*;
import java.net.*;
import java.util.*;

class ReverseProxy
{
	public static void main(String args[]) throws Exception
		{
			Tabela informacoes = new Tabela();
			Gestor gestor = new Gestor(informacoes);
			gestor.start();
			DatagramSocket socket = new DatagramSocket(5555);
			byte[] receiveData = new byte[1024];
				while(true)
					{
						DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
						socket.receive(receivePacket);
						String sentence = new String(receivePacket.getData(), 0, receivePacket.getLength());

						String[] pdu = sentence.split(" ");

						if (pdu[0].equals("registo")){
							enviarPedido(socket, receivePacket, informacoes);
						}
						else if (pdu[0].equals("resposta")){
							atualizarInformacao(receivePacket, informacoes, pdu[1]);
						}
						System.out.println("RECEBIDO: " + sentence);
					}
		}

		static void enviarPedido(DatagramSocket socket, DatagramPacket receivePacket, Tabela informacoes){
			InetAddress ipAddress;
			try{
				byte[] sendData = new byte[1024];
				ipAddress = receivePacket.getAddress();
				int port = receivePacket.getPort();
				String sentence = "pedido " + informacoes.enviar(ipAddress);
				sendData = sentence.getBytes();
				DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipAddress, port);
				socket.send(sendPacket);
			}
			catch (IOException e){}
		}

		static void atualizarInformacao(DatagramPacket receivePacket, Tabela informacoes, String id){
			InetAddress ipAddress = receivePacket.getAddress();
			informacoes.recebido(ipAddress, id);
		}
}

class Tabela{

	Map<InetAddress, Servidor> servidores;

	Tabela(){
		servidores = new HashMap<>();
	}

	String enviar(InetAddress ipAddress){
		Servidor s = servidores.get(ipAddress);
		if (s == null){
			s = new Servidor (ipAddress);
			servidores.put(ipAddress, s);
		}
		return s.enviar();
	}

	void recebido(InetAddress ipAddress, String id){
		Servidor s = servidores.get(ipAddress);
		s.recebido(id);
	}

	void remover(InetAddress ipAddress){
		servidores.remove(ipAddress);
	}
}

class Servidor{

	InetAddress ipAddress;
	float rtt;
	int totalEnviados; 
	int totalRecebidos;
	long ultimoEnviado;
	int conexoes;

	int id;

	Servidor(InetAddress ipAddress){
		this.ipAddress = ipAddress;
	}

	String enviar(){
		Date d = new Date();
		this.ultimoEnviado = d.getTime();
		this.totalEnviados++;

		return Integer.toString(id++);
	}

	void recebido(String id){
		if (Integer.toString(this.id-1).equals(id)){
			Date d = new Date();
			long tempo = d.getTime();
			rtt += (tempo - this.ultimoEnviado);
			rtt /= 2;
			this.totalRecebidos++;
		}
	}
}

class Gestor extends Thread {
	Tabela informacoes;

	Gestor(Tabela informacoes){ this.informacoes = informacoes; }

	public void run() {
		while(true) {
			try {
				for(InetAddress key : informacoes.servidores.keySet()) {
					Date d = new Date();
					long tempo = d.getTime();
					long diff = tempo - informacoes.servidores.get(key).ultimoEnviado;

					System.out.println(key + ": RTT -> " + informacoes.servidores.get(key).rtt + "ms   \t LastActive: " + diff + "ms");
					if(diff > 15000) informacoes.remover(key);
				}
				System.out.println("Número de Monitores Conectados: " + informacoes.servidores.size());
				Thread.sleep(5000);
			}
			catch (InterruptedException e) {}
		}
	}
}