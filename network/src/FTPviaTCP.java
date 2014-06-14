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
		String user;//�û���
		String password;//����
		String workDir;//�̹߳���·��
		public UserInfo(String u,String pwd,String wd){
			user=u;
			password=pwd;
			workDir=wd;
		}
	}
	
	class FtpState {
		final static int FS_WAIT_LOGIN = 0; // δ�����û���
		final static int FS_WAIT_PASS = 1; // δ��������
		final static int FS_LOGIN = 2; // �ѵ�¼
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
		//���ʼĿ¼Ϊ��Ŀ�е�ftpfile�ļ���
		initDir= System.getProperty("user.dir").replace('\\', '/')+"/ftpfile";
		usersInfo.add(new UserInfo("ym", "34", initDir));
		try {
			System.out.println("ftp server started!");
			// ����21�Ŷ˿�
			ServerSocket s = new ServerSocket(21);
			for (;;) {
				// ���ܿͻ�������
				Socket incoming = s.accept();
				BufferedReader in = new BufferedReader(new InputStreamReader(incoming.getInputStream()));
			    PrintWriter out = new PrintWriter(incoming.getOutputStream(),true);
				out.println("220 Service ready for new user,�ѵ�¼�û�����"+counter);
				// ���������߳�
				FtpHandler fh=new FtpHandler(incoming, i);
				fh.start();
				i++;
				counter++;
				//�����̼߳����û�����
				users.add(fh);
				
			}
		} catch (Exception e) {
		}
	}
	
	class FtpHandler extends Thread {
		Socket cSocket; // ����
		Socket dSocket; // ���� 
		ServerSocket s;
		int id;
		String cmd = ""; // ftpЭ������
		String param = ""; // ����
		String user;
		String remoteHost = " "; // IP
		int remotePort = 0; // TCP�˿ں�
		String dir = FTPviaTCP.initDir;
		String rootdir = System.getProperty("user.dir").replace('\\', '/')+"/ftpfile"; 
		int state = 0; 
		String reply;
		PrintWriter out;
		int type = FtpState.FTYPE_IMAGE; //�ļ�����
		String requestfile = "";
		boolean isrest = false;
		
		// FtpHandler����
		// ���췽��
		public FtpHandler(Socket s, int i) {
			cSocket = s;
			id = i;
		//	dir = FTPviaTCP.initDir;
		}

		// run�����������ͻ��������ָ��
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
						parseResult = parseInput(str); // ָ��ת��Ϊָ���
						System.out.println("CMD:" + cmd + " PARAM:" + param);
						System.out.print("->");
						switch (state) // �жϵ�ǰ�û��ĵ�¼״̬
						{
						case FtpState.FS_WAIT_LOGIN:
							finished = commandUSER();
							break;
						case FtpState.FS_WAIT_PASS:
							finished = commandPASS();
							break;
						case FtpState.FS_LOGIN: {
							switch (parseResult)// �жϵ�ǰ�û�������ָ��
							{
							case -1:
								errCMD(); // ������Э���׼��ָ��
								break;
							case 4:
								finished = commandCDUP(); // ������һ��Ŀ¼
								break;
							case 5:
								finished = commandCWD(); // ��ת��ָ����Ŀ¼
								break;
							case 6:
								finished = commandQUIT(); // �˳�
								break;
							case 7:
								finished = commandPORT(); // IP+TCP port
								break;
							case 8:
								finished = commandTYPE(); // ���ñ�������
								break;
							case 9:
								finished = commandRETR(); // �ӷ������л���ļ�
								break;
							case 11:
								finished = commandABOR(); // �ر��������ݴ�����׽ӿ�dSocket
								break;
							case 14:
								finished = commandLIST(); // �ļ���Ŀ¼���б�
								break;
							case 15:
							case 16:
								finished = commandPWD(); // "��ǰĿ¼" ��Ϣ
								break;
							case 17:
								finished = commandNOOP(); // "������ȷ" ��Ϣ
								break;
							case 18:
								finished = commandPASV(); // "������ȷ" ��Ϣ
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

		// parseInput����
		int parseInput(String s) {
			int p = 0;
			int i = -1;
			// �ж��Ƿ��в���������У��򽫲���������Param�����У������������cmd������
			p = s.indexOf(" ");
			if (p == -1) // ������޲�������
				cmd = s;
			else
				cmd = s.substring(0, p); // ����в�������

			if (p >= s.length() || p == -1)// ����޿ո�,���߿ո���s��
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

					boolean checkPASS(String s) // ��������Ƿ���ȷ,���ļ�����
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
					if (f.getParent() != null && (!dir.equals(rootdir)))// �и�·�� && ���Ǹ�·��
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

				boolean commandCWD()// ������ı乤��Ŀ¼��ָ����Ŀ¼
				{ 
					File f = new File(param);
					String s = "";
					String s1 = "";
					if (dir.endsWith("/"))
						s = dir.substring(0,dir.length()-1);
					else
						s = dir;
					File f1 = new File(s + param);//��תĿ¼

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

				//����Ϊ32λIP+16λTCP�˿ںţ�8λΪһ�飬ʹ��ʮ���ƴ��䣬�м��ö��Ÿ���
				boolean commandPORT() {
					int p1 = 0;
					int p2 = 0;
					int[] a = new int[6];// ��Ų���ת���������
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
				
				// �ӷ������л���ļ�
				boolean commandRETR() {
					requestfile = param;
					File f = new File(requestfile);
					if (!f.exists()) {
						f = new File(dealLatter(dir) + param);//����û�ֱ�������ļ�������
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
						//�����в�����׺����ʲôҲ������
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
								byte[] buf = new byte[1024]; // Ŀ�껺����
								int l = 0;
								while ((l = fin.read(buf, 0, 1024)) != -1) // ������δ����
								{
									dataout.write(buf, 0, l); // д���׽���
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
				
				// ǿ��dSocket ��
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
				
				//������������ȴ���������
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
