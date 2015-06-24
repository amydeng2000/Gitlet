import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.nio.file.Paths;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HashMap;
import java.io.Serializable;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class CommitNode implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final int HASHFRIEND = 0xff;
    private static int commitCount;
    private int commitID;
    private String sha;
    private String base;
    private String commitMessage;
    private String branchName;
    private Date d;
    private String dateFormatted;
    private CommitNode prev;
    private CommitNode replay;
    private HashMap<String, CommitNode> nextNodes;
    private HashSet<String> oldFiles;
    private HashSet<String> newFiles;
    private HashSet<String> allFiles;
    private HashSet<String> rmFiles;
    private HashMap<String, CommitNode> fileLastCommitted;
    private boolean isReplay, isSkip;

    /**
     * Constructs a new commit node. SHA 256 hashing from
     * http://stackoverflow.com
     * /questions/5531455/how-to-encode-some-string-with-sha256-in-java
     */
    public CommitNode(CommitNode p, String branch, String msg, HashSet<String> staged,
            HashSet<String> removed, CommitNode replayNode, boolean isRemote, boolean skip) {
        this.isReplay = isReplay;
        this.isSkip = skip;
        this.commitID = commitCount;
        commitCount += 1;
        prev = p;
        nextNodes = new HashMap<String, CommitNode>();
        replay = replayNode;
        isReplay = (replay != null);
        branchName = branch;
        commitMessage = msg;
        SimpleDateFormat A = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        d = new Date();
        dateFormatted = A.format(d);
        oldFiles = new HashSet<String>();
        newFiles = staged;
        rmFiles = removed;
        allFiles = new HashSet<String>(newFiles);
        this.sha = makeSHA();
        fileLastCommitted = new HashMap<String, CommitNode>();
        if (p != null) {
            HashMap<String, CommitNode> prevMap = p.getFileMap();
            for (String a : prevMap.keySet()) {
                if (!rmFiles.contains(a)) {
                    this.fileLastCommitted.put(a, prevMap.get(a));
                }
            }
            p.nextNodes.put(branch, this);
            p.addNext(branch, this);
        }
        for (String s : newFiles) {
            if (!isReplay && replayNode == null) {
                fileLastCommitted.put(s, this);
            } else {
                fileLastCommitted.put(s, replayNode);
            }
        }
        for (String r : rmFiles) {
            fileLastCommitted.remove(r);
            allFiles.remove(r);
        }
        retrieveOldFiles();
        try {
            if (!isReplay) {
                makeFolder(isRemote);
            }
        } catch (IOException ioe) {
            System.out.println("Couldn't make folder: " + ioe);
        }
    }

    /** Static initializer to initialize commitCount. */
    static {
        commitCount = 0;
    }

    /** Creates SHA string for THIS. */
    public String makeSHA() {
        base = "";
        sha = "";
        String xs = String.valueOf(this.commitID);
        int length = xs.length();
        xs = "00000000" + xs;
        xs = xs.substring(length, xs.length());
        base = this.commitMessage + d.toString();
        if (this.prev != null) {
            base = base + this.prev.getSHA();
        }
        base = base + xs;
        base = base + String.valueOf((int) (Math.random() * 1000));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes("UTF-8"));
            StringBuffer hexString = new StringBuffer();
            for (int i = 0; i < hash.length; i++) {
                String hex = Integer.toHexString(HASHFRIEND & hash[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (IOException | NoSuchAlgorithmException ex) {
            System.out.println(ex);
            return "";
        }
    }

    /** Returns true if the CommitNode is to be skipped. */
    public boolean isToBeSkipped() {
        return isSkip;
    }

    /** Returns true if THIS node is in the history of c. */
    public boolean isInHistory(CommitNode c) {
        if (c.getPrev() == null) {
            return false;
        }
        // System.out.println(this.getSHA() +" vs. "+ c.getPrev().getSHA());
        if (this.equals(c.getPrev())) {
            return true;
        }
        return this.isInHistory(c.getPrev());
    }

    /** Returns FileLastCommitted HashMap. */
    public HashMap<String, CommitNode> getFileMap() {
        return this.fileLastCommitted;
    }

    /** Finds the child node in the given branch. */
    public CommitNode next(String branchname) {
        if (nextNodes.containsKey(branchname)) {
            CommitNode t = nextNodes.get(branchname);
            if (!t.isToBeSkipped()) {
                return t;
            } else {
                return t.next(branchname);
            }
        } else {
            return null;
        }
    }

    /** Returns true if this commitNode has a next node in branchName. */
    public boolean containsNextIn(String branchname) {
        return this.next(branchname) != null;
    }

    /** Adds c as a child of THIS, in branchname. */
    public void addNext(String branchname, CommitNode c) {
        this.nextNodes.put(branchname, c);
    }

    /** For testing purposes. */
    public void printNext() {
        System.out.println("Next nodes for ID: " + sha + " - "); // SHA
        for (String branchname : nextNodes.keySet()) {
            System.out.println("Branch: " + branchname + ", Node ID: "
                    + nextNodes.get(branchname).getSHA()); // SHA
        }
    }

    /** Makes folder with path .gitlet/<commitID>, storing all newFiles. */
    public void makeFolder(boolean isRemote) throws IOException {
        String dirPath = ".gitlet/" + this.getSHA() + "/";
        if (isRemote) {
            dirPath = "remoteWD/" + dirPath;
        }
        // System.out.println("dest: "+dirPath);
        File f = new File(dirPath);
        f.mkdir();
        for (String filename : newFiles) {
            String to = dirPath + filename;
            String from = filename;
            Path fromPath = Paths.get(from);
            Path toPath = Paths.get(to);
            if (fromPath.getParent() != null) {
                Files.createDirectories(fromPath.getParent());
            }
            if (toPath.getParent() != null) {
                Files.createDirectories(toPath.getParent());
            }
            Files.copy(fromPath, toPath, REPLACE_EXISTING);
        }
    }

    /** Returns HashSet of all files in the commit. */
    public HashSet<String> getAllFiles() {
        return this.allFiles;
    }

    /** Returns HashSet of new files in the commit. */
    public HashSet<String> getNewFiles() {
        return this.newFiles;
    }

    /** Returns HashSet of removed files in the commit. */
    public HashSet<String> getRmFiles() {
        return this.rmFiles;
    }

    /** Returns previous/parent commitNode. */
    public CommitNode getPrev() {
        CommitNode t = this.prev;
        if (t != null) {
            if (!t.isToBeSkipped()) {
                return t;
            } else {
                return t.getPrev();
            }
        } else {
            return null;
        }
    }

    /** Returns message of this commit. */
    public String getMsg() {
        return this.commitMessage;
    }

    /** Returns integer commitID/commit no. of this commit. */
    public int getID() {
        return this.commitID;
    }

    /** Returns the base that was hashed into the String ID. */
    public String getSHABase() {
        return this.base;
    }

    /** Returns the SHA hash for the given commit. */
    public String getSHA() {
        return this.sha;
    }

    /** Returns the commit ID (wrt THIS) where the file was most recently added. */
    public CommitNode whereToFind(String filename) {
        return fileLastCommitted.get(filename);
    }

    /* For file access. Returns true if .gitlet/<SHA ID> contains File <filename>. */
    public boolean folderContainsFile(String filename) {
        return newFiles.contains(filename) && !isReplay;
    }

    /** Returns true if this commit contains filename. */
    public boolean fileExists(String filename) {
        return allFiles.contains(filename);
    }

    /** Returns File object as is in this commit. */
    public File getFile(String filename) {
        String from = "", reqSHA = "";
        if (this.folderContainsFile(filename)) {
            from = ".gitlet/" + sha + "/" + filename;
            return new File(from);
        } else {
            if (this.whereToFind(filename) == null && replay == null) {
                // System.out.println("Couldn't get file in commit ID " + sha);
                return null;
            } else {
                if (this.whereToFind(filename) != null) {
                    reqSHA = whereToFind(filename).getSHA();
                }
                /* if (replay != null) reqSHA = replay.getSHA(); */
                from = ".gitlet/" + reqSHA + "/" + filename;
                return new File(from);
            }
        }
    }

    /** Overrides equals to return if SHA IDs are equal. */
    @Override
    public boolean equals(Object o) {
        if (o instanceof CommitNode) {
            CommitNode oc = (CommitNode) o;
            return this.getSHA().equals(oc.getSHA()); // SHA
        } else {
            return false;
        }
    }

    /** Overrides hashCode to enable SHA hashing. */
    @Override
    public int hashCode() {
        return this.sha.hashCode(); // SHA
    }

    /** Retrieve old files from parent node. */
    private void retrieveOldFiles() {
        if (prev != null) {
            HashSet<String> oldAll = prev.getAllFiles();
            for (String filename : oldAll) {
                if (!newFiles.contains(filename) && !rmFiles.contains(filename)) {
                    oldFiles.add(filename);
                    allFiles.add(filename);
                }
            }
        }
    }

    /** Prints info of THIS commit as is needed for log function. */
    public void printInfo() {
        System.out.println("====");
        System.out.println("Commit " + this.sha + "."); // SHA
        System.out.println(this.dateFormatted);
        System.out.println(this.commitMessage + "\n");
    }

}
