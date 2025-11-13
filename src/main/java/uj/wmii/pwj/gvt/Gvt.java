package uj.wmii.pwj.gvt;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Gvt {

    private final ExitHandler exitHandler;
    private static final String GVT_FOLDER = ".gvt";
    private static final String STORAGE_DIR = "storage";
    private static final String META_FILE = "metadata.ser";

    public Gvt(ExitHandler exitHandler) {
        this.exitHandler = exitHandler;
    }
    public static void main(String... args) {
        Gvt gvt = new Gvt(new ExitHandler());
        gvt.mainInternal(args);
    }

    public void mainInternal(String... args) {
        if (args.length == 0) {
            exitHandler.exit(1, "Please specify command.");
            return;
        }

        String cmd = args[0];

        if ("init".equals(cmd)) {
            executeInit();
            return;
        }

        if (!isGvtInitialized()) {
            exitHandler.exit(-2, "Current directory is not initialized. Please use init command to initialize.");
            return;
        }

        try {
            switch (cmd) {
                case "add":
                    executeAdd(args);
                    break;
                case "detach":
                    executeDetach(args);
                    break;
                case "commit":
                    executeCommit(args);
                    break;
                case "checkout":
                    executeCheckout(args);
                    break;
                case "history":
                    executeHistory(args);
                    break;
                case "version":
                    executeVersion(args);
                    break;
                default:
                    exitHandler.exit(1, "Unknown command " + cmd + ".");
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
            exitHandler.exit(-3, "Underlying system problem. See ERR for details.");
        }
    }

    private void executeInit() {
        if (isGvtInitialized()) {
            exitHandler.exit(10, "Current directory is already initialized.");
            return;
        }

        try {
            File gvtDir = new File(GVT_FOLDER);
            if (!gvtDir.mkdir()) {
                throw new IOException("Cannot create directory");
            }

            File storageDir = new File(gvtDir, STORAGE_DIR);
            if (!storageDir.mkdir()) {
                throw new IOException("Cannot create storage directory");
            }

            Repository repo = new Repository();
            repo.addVersion("GVT initialized.", new HashMap<>());
            saveRepository(repo);

            exitHandler.exit(0, "Current directory initialized successfully.");
        } catch (Exception e) {
            e.printStackTrace(System.err);
            exitHandler.exit(-3, "Underlying system problem. See ERR for details.");
        }
    }

    private void executeAdd(String[] args) {
        String[] params = getFileParams(args);
        if (params.length == 0) {
            exitHandler.exit(20, "Please specify file to add.");
            return;
        }

        String filename = params[0];
        String msg = getUserMessage(args);

        try {
            File f = new File(filename);
            if (!f.exists() || !f.isFile()) {
                exitHandler.exit(21, "File not found. File: " + filename);
                return;
            }

            Repository repo = loadRepository();
            Map<String, String> currentFiles = repo.getLatestFiles();

            if (currentFiles.containsKey(filename)) {
                exitHandler.exit(0, "File already added. File: " + filename);
                return;
            }

            String hash = storeFile(f);
            Map<String, String> updatedFiles = new HashMap<>(currentFiles);
            updatedFiles.put(filename, hash);

            String commitMsg;
            if (msg != null && !msg.isEmpty()) {
                commitMsg = msg;
            } else {
                commitMsg = "File added successfully. File: " + filename;
            }

            repo.addVersion(commitMsg, updatedFiles);
            saveRepository(repo);

            exitHandler.exit(0, "File added successfully. File: " + filename);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            exitHandler.exit(22, "File cannot be added. See ERR for details. File: " + filename);
        }
    }

    private void executeDetach(String[] args) {
        String[] params = getFileParams(args);
        if (params.length == 0) {
            exitHandler.exit(30, "Please specify file to detach.");
            return;
        }

        String filename = params[0];
        String msg = getUserMessage(args);

        try {
            Repository repo = loadRepository();
            Map<String, String> currentFiles = repo.getLatestFiles();

            if (!currentFiles.containsKey(filename)) {
                exitHandler.exit(0, "File is not added to gvt. File: " + filename);
                return;
            }

            Map<String, String> updatedFiles = new HashMap<>(currentFiles);
            updatedFiles.remove(filename);

            String commitMsg;
            if (msg != null && !msg.isEmpty()) {
                commitMsg = msg;
            } else {
                commitMsg = "File detached successfully. File: " + filename;
            }

            repo.addVersion(commitMsg, updatedFiles);
            saveRepository(repo);

            exitHandler.exit(0, "File detached successfully. File: " + filename);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            exitHandler.exit(31, "File cannot be detached, see ERR for details. File: " + filename);
        }
    }

    private void executeCommit(String[] args) {
        String[] params = getFileParams(args);
        if (params.length == 0) {
            exitHandler.exit(50, "Please specify file to commit.");
            return;
        }

        String filename = params[0];
        String msg = getUserMessage(args);

        try {
            File f = new File(filename);
            if (!f.exists() || !f.isFile()) {
                exitHandler.exit(51, "File not found. File: " + filename);
                return;
            }

            Repository repo = loadRepository();
            Map<String, String> currentFiles = repo.getLatestFiles();

            if (!currentFiles.containsKey(filename)) {
                exitHandler.exit(0, "File is not added to gvt. File: " + filename);
                return;
            }

            String hash = storeFile(f);
            Map<String, String> updatedFiles = new HashMap<>(currentFiles);
            updatedFiles.put(filename, hash);

            String commitMsg;
            if (msg != null && !msg.isEmpty()) {
                commitMsg = msg;
            } else {
                commitMsg = "File committed successfully. File: " + filename;
            }

            repo.addVersion(commitMsg, updatedFiles);
            saveRepository(repo);

            exitHandler.exit(0, "File committed successfully. File: " + filename);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            exitHandler.exit(52, "File cannot be committed, see ERR for details. File: " + filename);
        }
    }

    private void executeCheckout(String[] args) {
        if (args.length < 2) {
            exitHandler.exit(60, "Invalid version number: ");
            return;
        }

        String versionStr = args[1];
        int versionNum;

        try {
            versionNum = Integer.parseInt(versionStr);
        } catch (NumberFormatException e) {
            exitHandler.exit(60, "Invalid version number: " + versionStr);
            return;
        }

        try {
            Repository repo = loadRepository();
            VersionSnapshot snapshot = repo.getVersionSnapshot(versionNum);

            if (snapshot == null) {
                exitHandler.exit(60, "Invalid version number: " + versionStr);
                return;
            }

            for (Map.Entry<String, String> entry : snapshot.files.entrySet()) {
                retrieveFile(entry.getKey(), entry.getValue());
            }

            exitHandler.exit(0, "Checkout successful for version: " + versionStr);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            exitHandler.exit(-3, "Underlying system problem. See ERR for details.");
        }
    }

    private void executeHistory(String[] args) {
        try {
            Repository repo = loadRepository();
            List<VersionSnapshot> versions = repo.getAllVersions();

            int startIdx = 0;
            if (args.length >= 3 && "-last".equals(args[1])) {
                try {
                    int count = Integer.parseInt(args[2]);
                    startIdx = Math.max(0, versions.size() - count);
                } catch (NumberFormatException e) {
                    // Ignore and show all
                }
            }

            StringBuilder output = new StringBuilder();
            for (int i = versions.size() - 1; i >= startIdx; i--) {
                VersionSnapshot v = versions.get(i);
                String firstLine = v.message.split("\n")[0];
                output.append(v.versionNumber).append(": ").append(firstLine).append("\n");
            }

            exitHandler.exit(0, output.toString());
        } catch (Exception e) {
            e.printStackTrace(System.err);
            exitHandler.exit(-3, "Underlying system problem. See ERR for details.");
        }
    }

    private void executeVersion(String[] args) {
        try {
            Repository repo = loadRepository();
            VersionSnapshot snapshot;

            if (args.length < 2) {
                snapshot = repo.getLatestVersion();
            } else {
                String versionStr = args[1];
                int versionNum;

                try {
                    versionNum = Integer.parseInt(versionStr);
                } catch (NumberFormatException e) {
                    exitHandler.exit(60, "Invalid version number: " + versionStr + ".");
                    return;
                }

                snapshot = repo.getVersionSnapshot(versionNum);
                if (snapshot == null) {
                    exitHandler.exit(60, "Invalid version number: " + versionStr + ".");
                    return;
                }
            }

            String output = "Version: " + snapshot.versionNumber + "\n" + snapshot.message;
            exitHandler.exit(0, output);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            exitHandler.exit(-3, "Underlying system problem. See ERR for details.");
        }
    }

    private boolean isGvtInitialized() {
        File gvtDir = new File(GVT_FOLDER);
        return gvtDir.exists() && gvtDir.isDirectory();
    }

    private String[] getFileParams(String[] args) {
        List<String> result = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            if ("-m".equals(args[i])) {
                break;
            }
            result.add(args[i]);
        }
        return result.toArray(new String[0]);
    }

    private String getUserMessage(String[] args) {
        for (int i = 1; i < args.length - 1; i++) {
            if ("-m".equals(args[i])) {
                String msg = args[i + 1];
                if (msg.startsWith("\"") && msg.endsWith("\"") && msg.length() >= 2) {
                    return msg.substring(1, msg.length() - 1);
                }
                return msg;
            }
        }
        return null;
    }

    private String storeFile(File source) throws IOException {
        String id = UUID.randomUUID().toString();
        Path destination = Paths.get(GVT_FOLDER, STORAGE_DIR, id);
        Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
        return id;
    }

    private void retrieveFile(String filename, String hash) throws IOException {
        Path source = Paths.get(GVT_FOLDER, STORAGE_DIR, hash);
        Path destination = Paths.get(filename);
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private Repository loadRepository() throws IOException, ClassNotFoundException {
        File metaFile = new File(GVT_FOLDER, META_FILE);
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(metaFile))) {
            return (Repository) ois.readObject();
        }
    }

    private void saveRepository(Repository repo) throws IOException {
        File metaFile = new File(GVT_FOLDER, META_FILE);
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(metaFile))) {
            oos.writeObject(repo);
        }
    }

    static class VersionSnapshot implements Serializable {
        int versionNumber;
        String message;
        Map<String, String> files;

        VersionSnapshot(int versionNumber, String message, Map<String, String> files) {
            this.versionNumber = versionNumber;
            this.message = message;
            this.files = new HashMap<>(files);
        }
    }

    static class Repository implements Serializable {
        private List<VersionSnapshot> snapshots;

        Repository() {
            this.snapshots = new ArrayList<>();
        }

        void addVersion(String message, Map<String, String> files) {
            int nextVersion = snapshots.isEmpty() ? 0 : snapshots.get(snapshots.size() - 1).versionNumber + 1;
            snapshots.add(new VersionSnapshot(nextVersion, message, files));
        }

        VersionSnapshot getLatestVersion() {
            return snapshots.isEmpty() ? null : snapshots.get(snapshots.size() - 1);
        }

        Map<String, String> getLatestFiles() {
            VersionSnapshot latest = getLatestVersion();
            return latest == null ? new HashMap<>() : new HashMap<>(latest.files);
        }

        VersionSnapshot getVersionSnapshot(int versionNumber) {
            for (VersionSnapshot snapshot : snapshots) {
                if (snapshot.versionNumber == versionNumber) {
                    return snapshot;
                }
            }
            return null;
        }

        List<VersionSnapshot> getAllVersions() {
            return new ArrayList<>(snapshots);
        }
    }
}
