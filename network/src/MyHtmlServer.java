

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class MyHtmlServer {
	//指定80端口启动http服务器，每收到一个请求就创建一个服务器响应线程
	public MyHtmlServer(){
		ServerSocket server;
		try {
			server = new ServerSocket(80);
		
			System.out.println("server start at 80 port...........");
			while (true) {
				Socket soc = server.accept();
				ServerThread s = new ServerThread(soc);
				s.start();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public MyHtmlServer(boolean test){
		testThread = new ServerThread(null);
	}
	
	public ServerThread testThread;

	//服务器响应线程
	class ServerThread extends Thread {
		Socket soc;
		public ServerThread(Socket soc) {
			this.soc = soc;
		}
		
		//读取文件内容，转化为byte数组
		public  byte[] getFileByte(String filename) {
			ByteArrayOutputStream baos=new ByteArrayOutputStream();
			try {
			
				File file=new File(filename);
				FileInputStream fis;
				fis = new FileInputStream(file);
				
				byte[] b=new byte[1024];
				int read = 0;
				while((read=fis.read(b))!=-1)
				{
					baos.write(b,0,read);
				}
				fis.close();
				baos.close();
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return baos.toByteArray();
		}

		//分析并规范化url，如请求带有参数，提取参数前内容，并规范url，最后 "/"要规范成"/test.html",如后缀为".htm"，转化成".html"
		public String getQueryResource(String queryurl){
			String queryresource=null;
			//去掉参数
			int index=queryurl.indexOf('?');
			if(index!=-1){
				queryresource=queryurl.substring(0,queryurl.indexOf('?'));
			}else{
				queryresource=queryurl;
			}
			//规范化
			if(queryresource.endsWith("/")){
				queryresource=queryresource+"test.html";
			}else if(queryresource.endsWith("test")){
				queryresource=queryresource+".html";
			}else if(queryresource.endsWith(".htm")){
				queryresource=queryresource+"l";
			}
			return queryresource;
		}	
	
		//根据用户请求的资源类型，设定http响应头的信息，判断用户请求文件类型
		public String getHead(String queryresource){
			String filename="";
			int index=queryresource.lastIndexOf("/");
			filename=queryresource.substring(index+1);
			String filetype=filename.substring(filename.indexOf(".")+1);
			if(filetype.equals("html")){
				return "HTTP/1.0200OK\n"+"Content-Type:text/html\n" + "Server:myserver\n" + "\n";
			}
			else if(filetype.equals("jpg")||filetype.equals("gif")||filetype.equals("png")){
				return "HTTP/1.0200OK\n"+"Content-Type:image/jpeg\n" + "Server:myserver\n" + "\n";
			}else{
				return null;
			}
		}

		public void run() {
			try {
				InputStream is = soc.getInputStream();
				OutputStream os = soc.getOutputStream();
				soc.setSoTimeout(5000); //设定超时时间
				int readint;
				char c;
				byte[] buf = new byte[1024];
				byte[] data = null;
				int state = 0;
				String method = "";//传输方式，GET或POST
				String queryurl = ""; //请求URL信息
				String queryresource = "";
				String head = "";
				boolean start = false;
				while (true) {
					readint = is.read();
					c = (char) readint;
					boolean space=Character.isWhitespace(readint);
					if(!space){
						start = true;
						method+=c;
					}
					if(start && space){
						break;
					}
				}
				start = false;
				while(true){
					readint = is.read();
					c = (char) readint;
					boolean space=Character.isWhitespace(readint);
					if(!space){
						start = true;
						queryurl+=c;
					}
					if(start && space){
						break;
					}
				}

				queryresource=getQueryResource(queryurl);
				head=getHead(queryresource);

				while (true) {
					data = getFileByte("http"+queryresource);
					if (data != null) {
						os.write(data);
						os.close();
						break;
					}
				}
			} catch (java.net.SocketTimeoutException e){
				System.out.println("time out");
				return;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	}
	
	public static void main(String[] args) {
		new MyHtmlServer();
	}
}
