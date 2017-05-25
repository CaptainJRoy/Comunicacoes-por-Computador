
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author core
 */
public class ReverseProxy {
    public static void main(String[] args){
        Table table = new Table();
        Thread monitorization = new Thread(new Monitorization(table));
        monitorization.start();
        Thread tcpProxy = new Thread(new TCPProxy(table));
        tcpProxy.start();

    }

    private static class Intermediation implements Runnable{
        Socket from, to;

        public Intermediation(Socket from, Socket to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public void run() {
          try {
            BufferedInputStream read = new BufferedInputStream(from.getInputStream());
            BufferedOutputStream write = new BufferedOutputStream(to.getOutputStream());

            while(to.isConnected() && from.isConnected()) {
              int readable = read.available();
              int n_read = 0;
              byte[] buf = { 0 };
              int tot = 0;
              while(readable != 0) {
                buf = Arrays.copyOf(buf, readable + n_read);
                n_read = read.read(buf, n_read, readable);
                tot += n_read;
                readable = read.available();
              }
              write.write(buf, 0, tot);
              write.flush();
            }
            write.close();
          }
          catch (IOException ex) {
            ex.printStackTrace();
          }
        }
    }

    private static class Monitorization implements Runnable{

        Table table;

        public Monitorization(Table table) {
            this.table = table;
        }

        @Override
        public void run() {
            try {
                DatagramSocket socket = new DatagramSocket(5555);
                Thread probeRequest = new Thread(new ProbeRequest(socket, this.table));
                probeRequest.start();
                byte[] buf = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buf, 1024);
                while(true){
                    try {
                        socket.receive(packet);
                        String data = new String(packet.getData(), 0, packet.getLength());
                        String[] pdu = data.split(" ");
                        InetAddress ip = packet.getAddress();
                        if (pdu[0].equals("pooling")){
                            this.table.available(ip);
                        }
                        else //if (pdu[0].equals("probe"))
                        {
                            this.table.receive(ip, pdu);
                        }


                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            } catch (SocketException ex) {
                ex.printStackTrace();
            }
        }
    }

    private static class ProbeRequest implements Runnable{

        DatagramSocket socket;
        Table table;

        public ProbeRequest(DatagramSocket socket, Table table) {
            this.socket = socket;
            this.table = table;
        }

        @Override
        public void run() {
            byte[] buf = new byte[1024];
            while(true){
                Collection<Server> servers = table.getServers();
                for (Server s : servers){
                    if(System.currentTimeMillis() - s.getLastAvailable() > 15000){
                        table.remove(s.getIP());
                    }
                    else{
                        try {
                            buf = s.send().getBytes();
                            DatagramPacket packet = new DatagramPacket(buf, buf.length, s.getIP(), 5555);
                            this.socket.send(packet);
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
                try {
                    Thread.sleep(5000);
                    table.printAll();
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    private static class Server {

        InetAddress ip;
        long lastAvailable;
        double rtt;
        int packetsSent;
        int nextID;
        Set<Integer> receivedIDs;
        int connections;

        public Server(InetAddress ip) {
            this.ip = ip;
            this.receivedIDs = new HashSet<>();
        }

        private long getLastAvailable() {
            return this.lastAvailable;
        }

        private void setLastAvailable(){
            this.lastAvailable = System.currentTimeMillis();
        }

        private String send() {
            this.packetsSent++;
            StringBuilder sb = new StringBuilder();
            sb.append("probe ");
            sb.append(nextID++);
            sb.append(" ");
            sb.append(System.currentTimeMillis());
            return sb.toString();
        }

        private InetAddress getIP() {
            return ip;
        }

        private void receive(int id, int connections, long sendTime, long receiveTime) {
            if(this.receivedIDs.contains(id)){ //duplicado
                return;
            }
            if (rtt != 0){
                rtt = ((receiveTime - sendTime) + rtt)/2;
            }
            else {
                rtt = receiveTime - sendTime;
            }
            this.receivedIDs.add(id);
            this.connections = connections;
        }

        private void printData() {
            StringBuilder sb = new StringBuilder();
            sb.append(this.ip.toString());
            sb.append(" -> ");
            sb.append(this.rtt);
            sb.append(" ms | ");
            float lost = ((float) this.packetsSent - this.receivedIDs.size())/this.packetsSent;
            sb.append(lost);
            sb.append(" | ");
            sb.append(this.connections);
            System.out.println(sb.toString());
        }

        private double getPriority() {
            float lost = ((float) this.packetsSent - this.receivedIDs.size())/this.packetsSent;
            return rtt * (1+lost) * (1+connections);
        }
    }

    private static class TCPProxy implements Runnable{

        Table table;

        public TCPProxy(Table table) {
            this.table = table;
        }

        @Override
        public void run() {
            try {
                ServerSocket socket = new ServerSocket(80);
                while(true){
                    Socket client = socket.accept();
                    InetAddress ip = this.table.getMonitor();
                    Socket monitor = new Socket(ip, 80);
                    Thread monitor_client = new Thread(new Intermediation(monitor, client));
                    Thread client_monitor = new Thread(new Intermediation(client, monitor));
                    monitor_client.start();
                    client_monitor.start();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private static class Table {

        Map<InetAddress, Server> servers;

        public Table() {
            this.servers = new HashMap<>();
        }

        private void available(InetAddress ip) {
            Server server = servers.get(ip);
            if (server == null){
                server = new Server(ip);
                this.servers.put(ip, server);
            }
            server.setLastAvailable();
        }

        private Collection<Server> getServers() {
            return this.servers.values();
        }

        private void remove(InetAddress ip) {
            this.servers.remove(ip);
        }

        private void receive(InetAddress ip, String[] pdu) {
            long receiveTime = System.currentTimeMillis();
            int id = Integer.parseInt(pdu[1]);
            int connections = Integer.parseInt(pdu[2]);
            long sendTime = Long.parseLong(pdu[3]);
            servers.get(ip).receive(id, connections, sendTime, receiveTime);

            //printAll();
        }

        private void printAll() {
            System.out.println("IP Address - RTT - Loss rate - TCP connections");
            for (Server s : this.servers.values()){
                s.printData();
            }
        }

        private InetAddress getMonitor() {
            InetAddress ip = null;
            double priority = Double.MAX_VALUE;
            for(Server s : servers.values()){
                double p = s.getPriority();
                if(p < priority){
                    priority = p;
                    ip = s.getIP();
                }
            }
            return ip;
        }


    }
}
