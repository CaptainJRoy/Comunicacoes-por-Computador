import java.io.*;
import java.net.*;
import java.util.*;

class ReverseProxy{
	
	public static void main(String args[]) throws Exception{
		Tabela informacoes = new Tabela();
		
		Thread monitorizacao = new Thread(new Monitorizacao(informacoes));
		monitorizacao.start();
		
		Thread proxyTCP = new Thread(new ProxyTCP(informacoes));
		proxyTCP.start();
	}
}

class Monitorizacao implements Runnable{
	Tabela informacoes;

	Monitorizacao(Tabela informacoes){
		this.informacoes = informacoes;
	}

	public void run(){
		try{
			DatagramSocket socket = new DatagramSocket(5555);
			while(true){
				DatagramPacket receberPacote = receberPacote(socket);
				String dados = new String(receberPacote.getData(), 0, receberPacote.getLength());
				String[] pdu = dados.split(" ");
				if(pdu[0].equals("resposta")){
					atualizarInformacao(receberPacote, pdu);
				}
				else if(pdu[0].equals("registo")){
					enviarPedido(socket, receberPacote);
				}
			}
		}
		catch(SocketException e){
			e.printStackTrace();
		}
	}

	DatagramPacket receberPacote(DatagramSocket socket){
		try{
			byte[] receberDados = new byte[1024];
			DatagramPacket receberPacote = new DatagramPacket(receberDados, receberDados.length);
			socket.receive(receberPacote);
			return receberPacote;
		}
		catch(IOException e){
			e.printStackTrace();
		}
		return null;
	}

	void enviarPedido(DatagramSocket socket, DatagramPacket receberPacote){
		InetAddress ipAddress;
		try{
			byte[] enviarDados = new byte[1024];
			ipAddress = receberPacote.getAddress();
			int port = receberPacote.getPort();
			String dados = "pedido " + informacoes.enviar(ipAddress);
			enviarDados = dados.getBytes();
			DatagramPacket enviarPacote = new DatagramPacket(enviarDados, enviarDados.length, ipAddress, port);
			socket.send(enviarPacote);
		}
		catch (IOException e){
			e.printStackTrace();
		}
	}

	void atualizarInformacao(DatagramPacket receberPacote, String[] pdu){
		InetAddress ipAddress = receberPacote.getAddress();
		informacoes.recebido(ipAddress, pdu[1], pdu[2], pdu[3]);
	}
}

class ProxyTCP implements Runnable{

	Tabela informacoes;

	ProxyTCP(Tabela informacoes){
		this.informacoes = informacoes;
	}

	public void run(){
		try{
			ServerSocket servidor = new ServerSocket(80);
			while(true){
				Socket cliente = servidor.accept();
				InetAddress ipAddress = informacoes.getMonitor();
				Thread c = new Thread(new Intermediacao(cliente, ipAddress));
				c.start();
			}
		}
		catch(IOException e){
			e.printStackTrace();
		}
	}
}

class Intermediacao implements Runnable{

	Socket cliente;
	InetAddress ipAddress; // monitor

	Intermediacao (Socket cliente, InetAddress ipAddress){
		this.cliente = cliente;
		this.ipAddress = ipAddress;
	}

	public void run(){
		try{
			BufferedReader fromCliente = new BufferedReader(new InputStreamReader(cliente.getInputStream()));
			PrintWriter toCliente = new PrintWriter(cliente.getOutputStream());
			Socket monitor = new Socket(ipAddress, 80);
			BufferedReader fromMonitor = new BufferedReader(new InputStreamReader(monitor.getInputStream()));
			PrintWriter toMonitor = new PrintWriter(monitor.getOutputStream());
			while(true){
				String s = fromCliente.readLine();
				toMonitor.println(s);
				toMonitor.flush();

				if (s == null)
					break;

				s = fromMonitor.readLine();
				toCliente.println(s);
				toCliente.flush();

			}
			cliente.close();
			monitor.close();
		}
		catch(IOException e){
			e.printStackTrace();
		}
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

	void recebido(InetAddress ipAddress, String id, String nTCP, String tempo){
		Servidor s = servidores.get(ipAddress);
		s.recebido(id, nTCP, tempo);
		imprimirTabela();
	}

	/*void remover(InetAddress ipAddress){
		servidores.remove(ipAddress);
	}*/

	InetAddress getMonitor(){
		InetAddress ipAddress = null;
		float prioridade = Float.MAX_VALUE;
		for (Servidor s : servidores.values()){
			float p = s.getPrioridade();
			if (p < prioridade){
				prioridade = p;
				ipAddress = s.getIpAddress();
			}
		}
		imprimirTabela();
		return ipAddress;
	}

	void imprimirTabela(){
		System.out.println("IP - RTT - Taxa - Conexões");
		for (Servidor s : servidores.values()){
			s.imprimirServidor();
		}
	}
}

class Servidor{

	InetAddress ipAddress;
	float rtt;
	int totalEnviados;
	int conexoes;

	long ultimoRecebido;

	int nextID;

	Set<Integer> idsRecebidos;

	Servidor(InetAddress ipAddress){
		this.ipAddress = ipAddress;
		idsRecebidos = new HashSet<>();
	}

	InetAddress getIpAddress(){
		return ipAddress;
	}

	float getPrioridade(){
		if ((new Date()).getTime() - this.ultimoRecebido > 15000){
			return Float.MAX_VALUE;
		}
		return rtt * conexoes * ((float) totalEnviados - idsRecebidos.size())/totalEnviados;
	}

	String enviar(){
		long tempoEnvio = (new Date()).getTime();
		this.totalEnviados++;
		return Integer.toString(nextID++) + " " + Long.toString(tempoEnvio);
	}

	void recebido(String stringID, String nTCP, String tempoEnvioExecucao){
		int id = Integer.parseInt(stringID);
		if (idsRecebidos.contains(id)){
			return;
		}
		else{
			idsRecebidos.add(id);
		}
		long tempoRececao = (new Date()).getTime();
		this.ultimoRecebido = tempoRececao;
			
		long tempo = tempoRececao - Long.parseLong(tempoEnvioExecucao);
			
		if (rtt != 0.0f){
			rtt = (rtt + tempo)/2;
		}
		else {
			rtt = tempo;
		}
		this.conexoes = Integer.parseInt(nTCP);
	}

	void imprimirServidor(){
		System.out.println(ipAddress + " - " + rtt + " - " + ((float) this.totalEnviados - this.idsRecebidos.size())/totalEnviados + " - " + conexoes);
	}

}
