import static org.junit.Assert.*;

import org.junit.Test;

public class MyHtmlServerTest {
	private MyHtmlServer.ServerThread test = new MyHtmlServer(true).testThread;

	@Test
	public void testGetQueryResource() {
		assertEquals(test.getQueryResource("http://172.17.152.21/test.html"),
				"http://172.17.152.21/test.html");
		assertEquals(test.getQueryResource("http://172.17.152.21/test.htm"),
				"http://172.17.152.21/test.html");
		assertEquals(test.getQueryResource("http://172.17.152.21/?wsdl"),
				"http://172.17.152.21/test.html");
		}
	
	@Test
	public void testGetHead() {
		assertEquals(test.getHead("http://172.17.152.21/test.html"),
				"HTTP/1.0200OK\n" + "Content-Type:text/html\n"
						+ "Server:myserver\n" + "\n");
	}
}
