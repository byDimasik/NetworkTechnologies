package ru.nsu.fit.g15205.shishlyannikov;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        ProxyServer server = new ProxyServer(10080);
        server.run();
    }
}
