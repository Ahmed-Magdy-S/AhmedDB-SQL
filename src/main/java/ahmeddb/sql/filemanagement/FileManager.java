package ahmeddb.sql.filemanagement;

import ahmeddb.sql.configuration.DataSourceConfigProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The FileManager class handles the actual interaction with the OS file system.
 * Its primary job is to implement methods that read and write pages to disk blocks.
 * Its read method seeks to the appropriate position in the specified file and
 * reads the contents of that block to the byte buffer of the specified page.
 * The write method is similar. The append method seeks to the end of the file and
 * writes an empty array of bytes to it, which causes the OS to automatically extend the file.
 * Note how the file manager always reads or writes a block-sized number of
 * bytes from a file and always at a block boundary. In doing so, the file manager ensures that
 * each call to read, write,or append will incur exactly one disk access
 */
public class FileManager {

    /**
     * A singleton instance of the file manager object, each database instance has only one file manager that deal with its files.
     */
    private static FileManager INSTANCE;

    /**
     * A logical block size inside the file (any db file has many blocks that all have the same sizes).
     */
    private static final int blockSize = DataSourceConfigProvider.getDataSourceConfig().getBlockSize();

    /**
     * The name of database is also the name of the directory which store all database related data files.
     */
    private static final String databaseName = DataSourceConfigProvider.getDataSourceConfig().getDatabaseName();

    /**
     * Each RandomAccessFile object in the map openFiles corresponds to an open file.
     * Note that files are opened in “rws” mode. The “rw” portion specifies that the file is open for reading and writing.
     * The “s” portion specifies that the operating system should not delay disk I/O in order to optimize disk performance;
     * instead, every write operation must be written immediately to the disk.
     * This feature ensures that the database engine knows exactly when disk writes occur, which will be especially important
     * for implementing the data recovery algorithms
     */
    private final Map<String, RandomAccessFile> openFiles = new HashMap<>();

    /**
     * The database name is used as the name of the folder that contains the files for the database;
     * this folder is located in the engine’s current directory.
     * If no such folder exists, then a folder is created for a new database
     */
    private FileManager(){
        createDatabaseDirectory();
    }

    /**
     * Create a database directory if it's not exist
     */
    private void createDatabaseDirectory() {
        Path directoryPath = Path.of(databaseName);
        try {
            if (Files.notExists(directoryPath)) Files.createDirectories(directoryPath);
        }
        catch (IOException ioException){
            throw new RuntimeException("Cannot create database directory", ioException.getCause());
        }
    }

    //make a thread-safe singleton object, so the instance is the same across all application
    public static FileManager getInstance(){
        if (INSTANCE == null){
            synchronized (FileManager.class){
                if (INSTANCE == null) INSTANCE = new FileManager();
            }
        }
        return INSTANCE;
    }

    /**
     *
     * @param filename the name of a db file that will be accessed to do operations on it, if the file not exist,
     *                 it will be created then get access to it.
     * @return the random access file of a db file to do operation on it.
     */
    public RandomAccessFile getRandomAccessFile(String filename) {
        //A random access file act as a stream to read from or write to a specific file.
        RandomAccessFile randomAccessFile = openFiles.get(filename);
        if (randomAccessFile == null){
            File dbFile = new File(databaseName,filename);
            try {
                randomAccessFile = new RandomAccessFile(dbFile,"rws");
            }
            catch (FileNotFoundException fileNotFoundException){
                throw new RuntimeException("Cannot access db file", fileNotFoundException.getCause());
            }
            openFiles.put(filename,randomAccessFile);
        }
        return randomAccessFile;
    }

    /**
     * The method transfer the contents of the specified block of file into the specified memory page,
     * so that it can be read from memory.
     * @param blockId the logical block reference
     * @param page the memory page (a byteBuffer in memory) that hold block contents
     * @return number of bytes read (loaded) into a memory page
     */
    public synchronized int read(BlockId blockId, Page page) {
        // get the required file to read from it.
        RandomAccessFile dbFile = getRandomAccessFile(blockId.fileName());

        try{
            //move the pointer (cursor) to the given starting block position
            dbFile.seek(blockId.number() * blockSize);

            //get the channel of a db file,
            //so that we can deal with byteBuffer to read bytes from the db file by
            //transferring bytes from the db file through this channel to be loaded into byteBuffer (memory allocated buffer).
            FileChannel fileChannel = dbFile.getChannel();

            //transferring the reading of sequence of bytes of block into byteBuffer (memory allocated buffer)
            return fileChannel.read(page.contents());
        }
        catch (IOException ioException){
            throw new RuntimeException("Cannot read from db file", ioException.getCause());
        }
    }

    /**
     * The method transfer the contents of the specified memory page into the specified block in file
     * @param blockId the logical block reference
     * @param page the memory page (a byteBuffer in memory) that hold block contents.
     * @return number of bytes written to a file.
     */
    public synchronized int write(BlockId blockId, Page page) {
        // get the required file to read from it.
        RandomAccessFile dbFile = getRandomAccessFile(blockId.fileName());

        try{
            //move the pointer (cursor) to the given starting block position
            dbFile.seek(blockId.number() * blockSize);

            //get the channel of a db file
            //so that we can transfer bytes from byteBuffer through this channel to be written in the db file.
            FileChannel fileChannel = dbFile.getChannel();

            //transferring and writing sequence of bytes of byteBuffer (page) to a db file.
            return fileChannel.write(page.contents());
        }
        catch (IOException ioException){
            throw new RuntimeException("Cannot write to db file", ioException.getCause());
        }
    }

}