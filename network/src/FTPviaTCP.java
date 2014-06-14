import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class FTPviaTCP{

	public static void main(String[] args) {
		new FTPviaTCP();
	}
	
	private Socket socketClient;
	private int counter=0;
	public static String initDir ="";
	public ArrayList<FtpHandler> users = new ArrayList<FtpHandler>();
	public static ArrayList<UserInfo> usersInfo = new ArrayList<UserInfo>();
	
	class UserInfo
	{
		String user;//用户名
		String password;//密码
		String workDir;//线程工作路径
		public UserInfo(String u,String pwd,String wd){
			user=u;
			password=pwd;
			workDir=wd;
		}
	}
	
	class FtpState {
		final static int FS_WAIT_LOGIN = 0; // 未输入用户名
		final static int FS_WAIT_PASS = 1; // 未输入密码
		final static int FS_LOGIN = 2; // 已登录
		final static int FTYPE_ASCII = 0;
		final static int FTYPE_IMAGE = 1;
		final static int FMODE_STREAM = 0;
		final static int FMODE_COMPRESSED = 1;
		final static int FSTRU_FILE = 0;
		final static int FSTRU_PAGE = 1;
	}

	public FTPviaTCP() {
		// TODO Auto-generated constructor stub
		int i = 0;
		//设初始目录为项目中的ftpfile文件夹
		initDir= System.getProperty("user.dir").replace('\\', '/')+"/ftpfile";
		usersInfo.add(new UserInfo("ym", "34", initDir));
		try {
			System.out.println("ftp server started!");
			// 监听21号端口
			ServerSocket s = new ServerSocket(21);
			for (;;) {
				// 接受客户端请求
				Socket incoming = s.accept();
				BufferedReader in = new BufferedReader(new InputStreamReader(incoming.getInputStream()));
			    PrintWriter out = new PrintWriter(incoming.getOutputStream(),true);
				out.println("220 Service ready for new user,已登录用户数："+counter);
				// 创建服务线程
				FtpHandler fh=new FtpHandler(incoming, i);
				fh.start();
				i++;
				counter++;
				//将该线程加入用户数组
				users.add(fh);
				
			}
		} catch (Exception e) {
		}
	}
	
	class FtpHandler extends Thread {
		Socket cSocket; // 控制
		Socket dSocket; // 数据 
		ServerSocket s;
		int id;
		String cmd = ""; // ftp协议命令
		String param = ""; // 参数
		String user;
		String remoteHost = " "; // IP
		int remotePort = 0; // TCP端口号
		String dir = FTPviaTCP.initDir;
		String rootdir = System.getProperty("user.dir").replace('\\', '/')+"/ftpfile"; 
		int state = 0; 
		String reply;
		PrintWriter out;
		int type = FtpState.FTYPE_IMAGE; //文件类型
		String requestfile = "";
		boolean isrest = false;
		
		// FtpHandler方法
		// 构造方法
		public FtpHandler(Socket s, int i) {
			cSocket = s;
			id = i;
		//	dir = FTPviaTCP.initDir;
		}

		// run方法，监听客户端输入的指令
		public void run() {
			String str = "";
			int parseResult; 

			try {
				BufferedReader in = new BufferedReader(
						new InputStreamReader(cSocket.getInputStream()));
				out = new PrintWriter(cSocket.getOutputStream(), true);
				state = FtpState.FS_WAIT_LOGIN; 
				boolean finished = false;
				while (!finished) {
					str = in.readLine(); 
					if (str == null)
						finished = true; 
					else {
						parseResult = parseInput(str); // 指令转化为指令号
						System.out.println("CMD:" + cmd + " PARAM:" + param);
						System.out.print("->");
						switch (state) // 判断当前用户的登录状态
						{
						case FtpState.FS_WAIT_LOGIN:
							finished = commandUSER();
							break;
						case FtpState.FS_WAIT_PASS:
							finished = commandPASS();
							break;
						case FtpState.FS_LOGIN: {
							switch (parseResult)// 判断当前用户给出的指令
							{
							case -1:
								errCMD(); // 不符合协议标准的指令
								break;
							case 4:
								finished = commandCDUP(); // 返回上一层目录
								break;
							case 5:
								finished = commandCWD(); // 跳转到指定的目录
								break;
							case 6:
								finished = commandQUIT(); // 退出
								break;
							case 7:
								finished = commandPORT(); // IP+TCP port
								break;
							case 8:
								finished = commandTYPE(); // 设置编码类型
								break;
							case 9:
								finished = commandRETR(); // 从服务器中获得文件
								break;
							case 11:
								finished = commandABOR(); // 关闭用于数据传输的套接口dSocket
								break;
							case 14:
								finished = commandLIST(); // 文件和目录的列表
								break;
							case 15:
							case 16:
								finished = commandPWD(); // "当前目录" 信息
								break;
							case 17:
								finished = commandNOOP(); // "命令正确" 信息
								break;
							case 18:
								finished = commandPASV(); // "命令正确" 信息
								break;
							}
						}
							break;

						}
					}
					System.out.println(reply);
					out.println(reply);
					out.flush();//

				}
				cSocket.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// parseInput方法
		int parseInput(String s) {
			int p = 0;
			int i = -1;
			// 判断是否有参数，如果有，则将参数放置在Param变量中，将命令放置在cmd变量中
			p = s.indexOf(" ");
			if (p == -1) // 如果是无参数命令
				cmd = s;
			else
				cmd = s.substring(0, p); // 如果有参数命令

			if (p >= s.length() || p == -1)// 如果无空格,或者空格不在s中
				param = "";
			else
				param = s.substring(p + 1, s.length());
			cmd = cmd.toUpperCase(); 

			if (cmd.equals("CDUP"))
				i = 4;
			if (cmd.equals("CWD"))
				i = 5;
			if (cmd.equals("QUIT"))
				i = 6;
			if (cmd.equals("PORT"))
				i = 7;
			if (cmd.equals("TYPE"))
				i = 8;
			if (cmd.equals("RETR"))
				i = 9;
			if (cmd.equals("ABOR"))
				i = 11;
			if (cmd.equals("LIST"))
				i = 14;
			if (cmd.equals("PWD"))
				i = 15;
			if (cmd.equals("NOOP"))
				i = 17;
			if (cmd.equals("XPWD"))
				i = 16;
			if (cmd.equals("PASV"))
				i = 18;
			return i;
		}
		
		
				boolean commandUSER() {
					if (cmd.equals("USER")) {
						reply = "331 User name okay, need password";
					user = param;
				  	state = FtpState.FS_WAIT_PASS;
					return false;
				}
				else
				{
					reply = "501 Syntax error in parameters or arguments";
					return true;

				}
				}

					boolean checkPASS(String s) // 检查密码是否正确,从文件中找
					{
						for (int i = 0; i < FTPviaTCP.usersInfo.size(); i++) {
							if (((UserInfo) FTPviaTCP.usersInfo.get(i)).user.equals(user)
									&& ((UserInfo) FTPviaTCP.usersInfo.get(i)).password
											.equals(s)) {
								rootdir = ((UserInfo) FTPviaTCP.usersInfo.get(i)).workDir;
								dir = ((UserInfo) FTPviaTCP.usersInfo.get(i)).workDir;
								return true;
							}
						}
						return false;
					}
				
				boolean commandPASS() {
					if (cmd.equals("PASS")) {
						if(checkPASS(param))
						{
							reply = "230 User logged in, proceed";
							state = FtpState.FS_LOGIN;
							System.out.println("Message: user "+user+" Form "+remoteHost+"Login");
							System.out.print("->");
							return false;
						}
						else
						{
							reply = "530 Not logged in";
							return true;
						}
					}
					else
					{
						reply = "501 Syntax error in parameters or arguments";
						return true;
					}
				}

				void errCMD() {
					reply = "500 Syntax error, command unrecognized";
				}
				
				boolean commandCDUP()
				{
					File f = new File(dir);
					if (f.getParent() != null && (!dir.equals(rootdir)))// 有父路径 && 不是根路径
					{
						dir = f.getParent().replace('\\', '/');
						reply = "200 Command okay";
					}
					else
					{
						reply = "550 Current directory has no parent";
					}

					return false;
				}// commandCDUP() end

				boolean commandCWD()// 该命令改变工作目录到指定的目录
				{ 
					File f = new File(param);
					String s = "";
					String s1 = "";
					if (dir.endsWith("/"))
						s = dir.substring(0,dir.length()-1);
					else
						s = dir;
					File f1 = new File(s + param);//跳转目录

					if (f.isDirectory() && f.exists()) {
						if (param.equals("..") || param.equals("../")) {
							if (dir.compareToIgnoreCase(rootdir) == 0) {
								reply = "550 The directory does not exists";
							} else {
								s1 = new File(dir).getParent();
								if (s1 != null) {
									dir = s1;
									reply = "250 Requested file action okay, directory change to "+dir;
								}
								else
									reply = "550 The directory does not exists";
							}
						} else if (param.equals(".") || param.equals("./")) {
							
						} else {
							dir = rootdir+param;
							reply = "250 Requested file action okay, directory change to "+dir;
						}
					} else if (f1.isDirectory() && f1.exists()) {
						dir = s + param;
						reply = "250 Requested file action okay, directory change to "+dir;
					} else
						reply = "501 Syntax error in parameters or arguments";

					return false;
					} // commandCDW() end

				boolean commandQUIT() {
					reply="221 Service closing control connection";
					return true;
				}// commandQuit() end

				//参数为32位IP+16位TCP端口号，8位为一组，使用十进制传输，中间用逗号隔开
				boolean commandPORT() {
					int p1 = 0;
					int p2 = 0;
					int[] a = new int[6];// 存放参数转换后的数据
					int i = 0; //
					try {
						while ((p2 = param.indexOf(",", p1)) != -1)
						{
							a[i] = Integer.parseInt(param.substring(p1, p2));
							p2 = p2 + 1;
							p1 = p2;
							i++;
						}
						a[i] = Integer.parseInt(param.substring(p1, param.length()));
					} catch (NumberFormatException e) {
						reply = "501 Syntax error in parameters or arguments";
						return false;
					}

					remoteHost = a[0] + "." + a[1] + "." + a[2] + "." + a[3];
					remotePort = a[4] * 256 + a[5];
					reply = "200 Command okay";
					return false;
				}// commandPort() end
				
				boolean commandTYPE() 
				{
					if (param.equals("A")) {
						type = FtpState.FTYPE_ASCII;// 0
						reply = "200 Command okay Change to ASCII mode";
					} else if (param.equals("I")) {
						type = FtpState.FTYPE_IMAGE;// 1
						reply = "200 Command okay Change to BINARY mode";
					} else
						reply = "504 Command not implemented for that parameter";

					return false;
				}

				String dealLatter(String s){
					if(s.endsWith("/")){
						s=s.substring(0,s.length()-1);
					}
					return s;
				}
				
				// 从服务器中获得文件
				boolean commandRETR() {
					requestfile = param;
					File f = new File(requestfile);
					if (!f.exists()) {
						f = new File(dealLatter(dir) + param);//如果用户直接输入文件的名称
						if (!f.exists()) {
							reply = "550 File not found";
							return false;
						}
						requestfile = dealLatter(dir) + param;
					}
					
					if(!param.contains(".")){
						isrest = true;
					}
					if (isrest) {
						//参数中不含后缀名，什么也不用做
					} else {
						if (type == FtpState.FTYPE_IMAGE) 
						{
							try {
								out.println("150 Opening Binary mode data connection for "
										+ requestfile);
								dSocket = s.accept();
							//	dSocket = new Socket(remoteHost,remotePort,InetAddress.getLocalHost(),20);
								BufferedInputStream fin = new BufferedInputStream(
										new FileInputStream(requestfile));
								PrintStream dataout = new PrintStream(
										dSocket.getOutputStream(), true);
								byte[] buf = new byte[1024]; // 目标缓冲区
								int l = 0;
								while ((l = fin.read(buf, 0, 1024)) != -1) // 缓冲区未读满
								{
									dataout.write(buf, 0, l); // 写入套接字
								}
								fin.close();
								dataout.close();
								dSocket.close();
								reply ="226 Transfer complete !";

							} catch (Exception e) {
								e.printStackTrace();
								reply = "451 Requested action aborted: local error in processing";
								return false;
							}

						}
						if (type == FtpState.FTYPE_ASCII)
						{
							try {
								out.println("150 Opening ASCII mode data connection for "+ requestfile);
								dSocket = s.accept();
							//	dSocket = new Socket(remoteHost,remotePort,InetAddress.getLocalHost(),20);
								BufferedReader fin = new BufferedReader(new FileReader(
										requestfile));
								PrintWriter dataout = new PrintWriter(
										dSocket.getOutputStream(), true);
								String s;
								while ((s = fin.readLine()) != null) {
									dataout.println(s); 
								}
								fin.close();
								dataout.close();
								dSocket.close();
								reply ="226 Transfer complete !";
							} catch (Exception e) {
								e.printStackTrace();
								reply = "451 Requested action aborted: local error in processing";
								return false;
							}
						}
					}
					return false;

				}
				
				// 强关dSocket 流
				boolean commandABOR() {
					try {
						dSocket.close();
					} catch (Exception e) {
						e.printStackTrace();
						reply = "451 Requested action aborted: local error in processing";
						return false;
					}
					reply = "421 Service not available, closing control connection";
					return false;
				}
				
				
				boolean commandLIST()
				{
					try {
					//	dSocket = new Socket(remoteHost, remotePort,InetAddress.getLocalHost(), 20);
						dSocket=s.accept();
						PrintWriter dout = new PrintWriter(
								dSocket.getOutputStream(), true);
						if (param.equals("") || param.equals("LIST")) {
							out.println("150 Opening ASCII mode data connection for /bin/ls. ");
							File f = new File(dir);
							dout.println("DIR");
							String[] dirStructure = f.list();
							String fileType;
							for (int i = 0; i < dirStructure.length; i++) {
								if (dirStructure[i].indexOf(".") != -1) {
									fileType = "- "; 
								} else {
									fileType = "d "; 
								}
								dout.println(dirStructure[i]);
							}
						}
						dout.close();
						dSocket.close();
						reply = "226 Transfer complete !";
					} catch (Exception e) {
						e.printStackTrace();
						reply = "451 Requested action aborted: local error in processing";
						return false;
					}

					return false;
				}// commandLIST() end

				

				boolean commandPWD() {
					reply = "257 " + dir + " is current directory.";
					return false;
				}

				boolean commandNOOP() {
					reply = "200 OK.";
					return false;
				}
				
				//请求服务器，等待数据连接
				boolean commandPASV(){
					try {
						s = new ServerSocket(0);
						InetAddress addr = InetAddress.getLocalHost();
						String ip=addr.getHostAddress().toString();
						ip = ip.replace('.', ',');
						String highport = ""+s.getLocalPort()/256;
						String lowport = ""+s.getLocalPort()%256;
						reply = "227 Correct ("+ip+","+highport+","+lowport+")";
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					return false;
				}//commandPASV() end

		

		

		
		

		

	

		

	}


	
}
