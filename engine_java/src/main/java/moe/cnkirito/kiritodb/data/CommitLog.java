package moe.cnkirito.kiritodb.data;

import moe.cnkirito.kiritodb.common.Constant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.nio.ch.DirectBuffer;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static moe.cnkirito.kiritodb.common.UnsafeUtil.UNSAFE;

/**
 * @author kirito.moe@foxmail.com
 * Date 2018-10-28
 */
public class CommitLog {

    private static Logger logger = LoggerFactory.getLogger(CommitLog.class);
    // buffer
    private static ThreadLocal<ByteBuffer> bufferThreadLocal = ThreadLocal.withInitial(() -> ByteBuffer.allocate(Constant.ValueLength));
    private FileChannel fileChannel;
    // 逻辑长度 要乘以 4096
    private int fileLength;
    private ByteBuffer writeBuffer;
    private long addresses;
    private Lock lock = new ReentrantLock();

    public void init(String path, int no) throws IOException {
        File dirFile = new File(path);
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }
        File file = new File(path + Constant.DataName + no + Constant.DataSuffix);
        if (!file.exists()) {
            file.createNewFile();
        }
        this.fileChannel = new RandomAccessFile(file, "rw").getChannel();
        this.fileLength = (int) (this.fileChannel.size() / Constant.ValueLength);
        this.writeBuffer = ByteBuffer.allocateDirect(Constant.ValueLength);
        this.addresses = ((DirectBuffer) this.writeBuffer).address();
    }

    public void destroy() throws IOException {
        if (this.fileChannel != null) {
            this.fileChannel.close();
        }
    }

    public byte[] read(long offset) throws IOException {
        ByteBuffer buffer = bufferThreadLocal.get();
        buffer.clear();
        this.fileChannel.read(buffer, offset);
        return buffer.array();
    }

    public int write(byte[] data) {
        lock.lock();
        try {
            int offsetInt = fileLength++;
            UNSAFE.copyMemory(data, 16, null, addresses, 4096);
            this.writeBuffer.position(0);
            try {
                this.fileChannel.write(this.writeBuffer);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return offsetInt;
        } finally {
            lock.unlock();
        }

    }

    public int getFileLength() {
        return this.fileLength;
    }

}