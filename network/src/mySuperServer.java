
public class mySuperServer {
	public static void main(String[] args)
	{
		new Thread(){
			public void run()
			{
				new FTPviaTCP();
			}
		}.start();
		new Thread(){
			public void run()
			{
				new MyHtmlServer();
			}
		}.start();
	}
}
