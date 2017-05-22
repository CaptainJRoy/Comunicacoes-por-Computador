import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
*/

/**
*
 * @author core
 */
public class MonitorUDP {
    public static void main(String[] args){
        try {
            DatagramSocket socket = new DatagramSocket(5555);
            InetAddress ip = InetAddress.getByName(args[0]);
            Thread pooling = new Thread(new Pooling(socket, ip));
            pooling.start();
            Thread probeReply = new Thread(new ProbeReply(socket, ip));
            probeReply.start();

            Runtime.getRuntime().exec(new String[] {"bash", "-c", "/etc/init.d/apache2 start"});
            Runtime.getRuntime().exec(new String[] {"bash", "-c", "sudo mkdir /run/lock"});
            Runtime.getRuntime().exec(new String[] {"bash", "-c", "sudo mkdir /var/log/apache2/"});
            Runtime.getRuntime().exec(new String[] {"bash", "-c", "sudo touch /var/log/apache2/{access,error,other_vhosts_access,suexec}.log"});
            Runtime.getRuntime().exec(new String[] {"bash", "-c", "sudo chown -R root:adm /var/log/apache2/"});
            Runtime.getRuntime().exec(new String[] {"bash", "-c", "sudo chmod -R 750 /var/log/apache2"});
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static class Pooling implements Runnable{

        DatagramSocket socket;
        DatagramPacket packet;

        public Pooling(DatagramSocket socket, InetAddress ip) {
            this.socket = socket;
            byte[] buf = new byte[1024];
            String data = "pooling";
            buf = data.getBytes();
            this.packet = new DatagramPacket(buf, buf.length, ip, 5555);
        }

        @Override
        public void run() {
            while(true){
                try {
                    socket.send(packet);
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

            }
        }
    }

    private static class ProbeReply implements Runnable{

        DatagramSocket socket;
        InetAddress ip;

        public ProbeReply(DatagramSocket socket, InetAddress ip) {
            this.socket = socket;
            this.ip = ip;
        }

        @Override
        public void run() {
            while(true){
                try {
                    byte[] buf = new byte[1024];
                    DatagramPacket packet = new DatagramPacket(buf, 1024);
                    socket.receive(packet);

                    long startTime = System.currentTimeMillis();
                    String data = new String(packet.getData(), 0, packet.getLength());
                    String[] pdu = data.split(" ");
                    StringBuilder sb = new StringBuilder();
                    sb.append(pdu[0]);
                    sb.append(" ");
                    sb.append(pdu[1]);
                    sb.append(" ");
                    Process proc = Runtime.getRuntime().exec(new String[] {"bash", "-c", "netstat -an | grep tcp | wc -l"});
                    BufferedReader br = new BufferedReader (new InputStreamReader (proc.getInputStream()));
                    sb.append(br.readLine());
                    sb.append(" ");
                    long time = Long.parseLong(pdu[2]) - startTime;
                    buf = new byte[1024];
                    time += System.currentTimeMillis(); //+= endTime
                    sb.append(time);

                    buf = sb.toString().getBytes();
                    packet = new DatagramPacket(buf, buf.length, ip, 5555);
                    socket.send(packet);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
}
