package client.util;

import client.user.UserContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Util {

    private static final String USER_DIR = "users";
    private static final String TX_ID_DIR = "tx_ids";

    private static String workingDirectory;

    public static void writeUserContext(UserContext userContext) throws IOException {
        String directoryPath = workingDirectory + "/" + USER_DIR + "/" + userContext.getAffiliation();
        String filePath = directoryPath + "/" + userContext.getName() + ".ser";
        File directory = new File(directoryPath);
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                log.error("Could not create directory {}!", directoryPath);
            }
        }
        FileOutputStream file = new FileOutputStream(filePath);
        ObjectOutputStream out = new ObjectOutputStream(file);
        out.writeObject(userContext);
        out.close();
        file.close();
    }

    public static UserContext readUserContext(String affiliation, String username) throws IOException, ClassNotFoundException {
        String filePath = String.join("/", workingDirectory, USER_DIR, affiliation, username) + ".ser";
        File file = new File(filePath);
        if (file.exists()) {
            FileInputStream fileStream = new FileInputStream(filePath);
            ObjectInputStream in = new ObjectInputStream(fileStream);
            UserContext uContext = (UserContext) in.readObject();
            in.close();
            fileStream.close();
            return uContext;
        }
        return null;
    }

    public static void setWorkingDirectory(String workingDirectory) {
        Util.workingDirectory = workingDirectory;
    }

    public static void cleanUp() {
        deleteDirectory(new File(workingDirectory + "/" + USER_DIR));
        deleteDirectory(new File(workingDirectory + "/" + TX_ID_DIR));
    }

    private static boolean deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            for (File child : Objects.requireNonNull(children)) {
                boolean success = deleteDirectory(child);
                if (!success) {
                    return false;
                }
            }
        }
        log.info("Deleting - {}", dir.getName());
        return dir.delete();
    }
}
