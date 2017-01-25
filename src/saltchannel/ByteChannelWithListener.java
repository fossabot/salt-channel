package saltchannel;

/**
 * Adds a listener to an existing ByteChannel. Decorator pattern.
 * 
 * @author Frans Lundberg
 */
public class ByteChannelWithListener implements ByteChannel {
    private ByteChannel channel;
    private Listener listener;
    
    public ByteChannelWithListener(ByteChannel channel, Listener listener) {
        this.channel = channel;
        this.listener = listener;
    }

    @Override
    public byte[] read() throws ComException {
        listener.onPreRead();
        byte[] bytes = channel.read();
        listener.onPostRead(bytes);
        return bytes;
    }

    @Override
    public void write(byte[]... byteArrays) throws ComException {
        listener.onPreWrite(byteArrays);
        channel.write(byteArrays);
        listener.onPostWrite(byteArrays);
    }
    
    public static interface Listener {
        public void onPreRead();
        public void onPostRead(byte[] bytes);
        public void onPreWrite(byte[][] byteArrays);
        public void onPostWrite(byte[][] byteArrays);
    }
}