import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.io.BufferedReader;
import java.nio.file.Paths;
import java.util.Arrays;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Stack;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class Gitlet implements Serializable {
    private static final long serialVersionUID = 1L;
    private CommitNode head;
    private int commitID;
    private HashSet<String> stagedFiles;
    private HashSet<String> rmFiles;
    private HashMap<String, CommitNode> b2h;
    // SHA HashMap<Integer, CommitNode> id2c;
    private HashMap<CommitNode, HashMap<String, Branch>> h2b;
    private HashMap<Integer, CommitNode> id2c;
    private HashMap<String, HashSet<String>> msg2ids;
    private Branch currBranch;
    private Branch commitTree;
    private String currName;
    // SHA remote features:
    private HashMap<String, String> r2login;
    private HashMap<String, String> r2loc;
    private HashMap<String, CommitNode> sha2c;
    private String firstSHA; // SHA of "initial commit " 0.
    private HashSet<String> dangerousMethods;

    /** Constructor that initializes all variables. */
    public Gitlet() {
        head = null;
        currBranch = null;
        commitTree = null;
        commitID = 0;
        currName = "master";
        firstSHA = "";
        stagedFiles = new HashSet<String>();
        rmFiles = new HashSet<String>();
        b2h = new HashMap<String, CommitNode>();
        h2b = new HashMap<CommitNode, HashMap<String, Branch>>();
        id2c = new HashMap<Integer, CommitNode>();
        sha2c = new HashMap<String, CommitNode>();
        msg2ids = new HashMap<String, HashSet<String>>();
        r2loc = new HashMap<String, String>();
        r2login = new HashMap<String, String>();
        dangerousMethods = new HashSet<String>();
    }

    /**
     * Prints all data structures. For testing purposes.
     */
    public void printAllDS() {
        System.out.println("~~~Head:");
        if (head != null) {
            head.printInfo();
        }
        System.out.println("~~~Current Branch:" + currName);
        System.out.println("~~~commitID:" + commitID);
        System.out.println("~~~Staged files:" + stagedFiles);
        System.out.println("~~~Removed files:" + rmFiles);
        System.out.println("~~~b2h:~~~");
        for (String b : b2h.keySet()) {
            System.out.println("Branch name: " + b + ", Head: " + b2h.get(b).getID());
        }
        System.out.println("~~~h2b:~~~~");
        for (CommitNode c : h2b.keySet()) {
            System.out.println("Head: " + c.getID() + ", Head: " + h2b.get(c));
        }
        System.out.println("~~~sha2c:~~~~");
        for (String sha : sha2c.keySet()) {
            System.out.println("ID: " + sha + ", New Files: " + sha2c.get(sha).getNewFiles());
        }

    }

    /** Saves the current state of Gitlet. */
    private static void save(Gitlet g) {
        try {
            File saveGitlet = new File(".gitlet/savedGitlet.ser");
            FileOutputStream fileOut = new FileOutputStream(saveGitlet);
            ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
            objectOut.writeObject(g);
            objectOut.close();
            fileOut.close();
        } catch (IOException e) {
            String msg = "IOException while saving gitlet state!";
            System.out.println(msg);
        }

    }

    /** Reads the saved state of Gitlet, if it exists. */
    private static Gitlet readSaved() {
        Gitlet prevGitlet = null;
        if (new File(".gitlet/savedGitlet.ser").exists()) {
            try {
                FileInputStream fin = new FileInputStream(".gitlet/savedGitlet.ser");
                ObjectInputStream ois = new ObjectInputStream(fin);
                Object historyObject = ois.readObject();
                prevGitlet = (Gitlet) historyObject;
                /*
                 * this.currBranch = prevGitlet.currBranch; this.commitID =
                 * prevGitlet.commitID; this.currName = prevGitlet.currName;
                 * this.head = prevGitlet.head; this.stagedFiles =
                 * prevGitlet.stagedFiles; this.rmFiles = prevGitlet.rmFiles;
                 * this.b2h = prevGitlet.b2h; this.h2b = prevGitlet.h2b;
                 * this.id2c = prevGitlet.id2c;
                 */
                ois.close();
                fin.close();
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Could not read previous gitlet state!");
                System.out.println(e);
                System.exit(1);
            }
        }
        return prevGitlet;
    }

    /** Creates .gitlet directory if it doesn't already exist. */
    private void initialize() {
        File dir = new File(".gitlet");
        if (!dir.exists()) {
            dir.mkdir();
            this.commit("initial commit", true);
        } else {
            System.out.println("A gitlet version control system already exists in the current"
                    + " directory.");
        }

    }

    /**
     * If the file had been marked for removal, unmark it. If the file does not
     * exist, print an error message. If the file has not been modified since
     * the last commit, aborts and prints an error message. Otherwise, stage it.
     */
    private void addFile(String filename) {
        File f = new File(filename);
        if (!f.exists()) {
            System.out.println("File does not exist.");
            return;
        }
        if (rmFiles.contains(filename)) {
            rmFiles.remove(filename);
            return;
        }
        if (head != null) {
            // System.out.println(f+" compared to "+head.getFile(filename));
            if (fileEquals(f, head.getFile(filename))) {
                System.out.println("File has not been modified since the last commit.");
                return;
            }
        }
        stagedFiles.add(filename);
    }

    /** Reads in bytes from File1 and File2. Returns true if contents are equal. */
    private boolean fileEquals(File file1, File file2) {
        if (file1 != null && file2 != null) {
            try {
                byte[] fp1 = Files.readAllBytes(file1.toPath());
                byte[] fp2 = Files.readAllBytes(file2.toPath());
                return Arrays.equals(fp1, fp2);
            } catch (IOException e) {
                System.out.println("Couldn't compare files: " + file1.getName() + " "
                        + file2.getName() + " : " + e);
            }
        }
        return false;
    }

    /**
     * Unstages the file. Mark the file for removal; this means it will not be
     * inherited as an old file in the next commit.
     */
    private void removeFile(String filename) {
        File f = new File(filename);
        /*
         * if (!f.exists()) { System.out.println("File does not exist.");
         * return; }
         */
        if (head != null) {
            if (!head.getAllFiles().contains(filename) && !stagedFiles.contains(filename)) {
                System.out.println("No reason to remove the file.");
                return;
            }
        }
        if (head == null && !stagedFiles.contains(filename)) {
            System.out.println("No reason to remove the file.");
            return;
        }
        if (stagedFiles.contains(filename)) {
            stagedFiles.remove(filename);
        } else {
            rmFiles.add(filename);
        }
    }

    /**
     * Commits the current commit node and updates data structures accordingly.
     * Stores the id for "initial commit" too.
     */
    private void commit(String msg, Boolean isFirst) {
        if (!isFirst && stagedFiles.isEmpty() && rmFiles.isEmpty()) {
            System.out.println("No changes added to the commit.");
            return;
        }
        if (msg.equals("")) {
            System.out.println("Please enter a commit message.");
            return;
        }
        CommitNode c = new CommitNode(head, currName, msg, new HashSet<String>(stagedFiles),
                new HashSet<String>(rmFiles), null, false, false);
        String sha = c.getSHA();
        if (head == null) {
            firstSHA = sha;
        }
        if (msg2ids.containsKey(msg)) {
            msg2ids.get(msg).add(sha);
            // SHA msg2ids.get(msg).add(commitID);
        } else {
            HashSet<String> tempSet = new HashSet<String>();
            // SHA HashSet<Integer> tempSet = new HashSet<Integer>();
            tempSet.add(sha);
            msg2ids.put(msg, tempSet);
        }
        Branch b = new Branch(c, currName);
        if (currBranch != null) {
            currBranch.addSubranch(b);
        } else {
            commitTree = b;
        }
        currBranch = b;
        /* currBranch.setHead(c); */
        head = c;
        b2h.put(currName, c);

        HashMap<String, Branch> temp = new HashMap<String, Branch>();
        temp.put(currName, b);
        h2b.put(c, temp);
        sha2c.put(c.getSHA(), c);
        id2c.put(commitID, c);
        commitID += 1;
        stagedFiles = new HashSet<String>();
        rmFiles = new HashSet<String>();
    }

    /**
     * Creates a branch with its head pointing to the current head. Does NOT
     * checkout to branch.
     */
    public void branch(String name) {
        if (b2h.containsKey(name)) {
            System.out.println("A branch with that name already exists.");
        } else {
            Branch b = new Branch(head, name);
            if (currBranch != null) {
                currBranch.addSubranch(b);
            } else {
                commitTree = b;
            }
            /* b.setHead(head); */
            CommitNode c = head;
            b2h.put(name, c);
            if (h2b.containsKey(c)) {
                h2b.get(c).put(name, b);
            } else {
                HashMap<String, Branch> temp = new HashMap<String, Branch>();
                temp.put(name, b);
                h2b.put(c, temp);
            }
        }
    }

    /** Old version of reset, just kept here for reference purposes. */
    private void reset(String idString) {
        int id;
        try {
            id = Integer.parseInt(idString);
            CommitNode c = id2c.get(id);
            if (id <= commitID && id >= 0) {
                restoreAllFiles(c);
            } else {
                System.out.println("No commit with that id exists.");
                return;
            }
            head = c;
            b2h.put(currName, c);
            h2b.get(c).put(currName, currBranch);
        } catch (NumberFormatException n) {
            System.out.println("Invalid ID format.");
        }

    }

    /** Resets all files to their versions in the commit with the given (SHA) id. */
    private void resetSHA(String sha) {
        CommitNode c = sha2c.get(sha);
        if (c != null) {
            restoreAllFiles(c);
            head = c;
            b2h.put(currName, c);
            if (h2b.containsKey(c)) {
                h2b.get(c).put(currName, currBranch);
            } else {
                HashMap<String, Branch> temp = new HashMap<String, Branch>();
                temp.put(currName, currBranch);
                h2b.put(c, temp);
            }
        } else {
            System.out.println("No commit with that id exists.");
        }
    }

    /**
     * This method has 3 versions: 1. Restores file in working directory to its
     * state at the commit at the branchead. 2. Restores the given file in the
     * working directory to its state at the given commit. 3. Restores all files
     * in the working directory to their versions in the commit at the head of
     * the given branch. Considers the given branch to now be the current
     * branch.
     */
    private void checkout(String[] args) {
        int id;
        String s, sha;
        File f;
        int actualLen = 0;
        for (int i = 0; i < args.length; i++) {
            // System.out.println(i+":"+args[i]);
            if (args[i] != null) {
                actualLen += 1;
            }
        }
        // System.out.println("Actual length of args: "+actualLen);
        if (actualLen == 1) {
            s = args[0];
            f = new File(s);
            if (b2h.containsKey(s)) { // checkout [branch name]
                if (s.equals(currName)) {
                    System.out.println("No need to checkout the current branch.");
                    return;
                } else {
                    currName = s;
                    CommitNode reqHead = b2h.get(s);
                    Branch reqBranch = h2b.get(reqHead).get(s);
                    head = reqHead;
                    currBranch = reqBranch;
                    restoreAllFiles(head);
                }
            } else if (f.exists()) { // checkout [file name]
                restoreFile(s, head, false);
            } else {
                System.out.println("File does not exist in the most recent commit,"
                        + " or no such branch exists.");
            }

        } else if (actualLen == 2) { // checkout [commit id] [file name]
            sha = args[0];
            s = args[1];
            // SHA try {
            // SHA id = Integer.parseInt(args[0]);
            // System.out.println(id);
            // SHA CommitNode c = id2c.get(id);
            CommitNode c = sha2c.get(sha);
            // System.out.println(c.getID());
            // SHA if (id <= commitID) {
            if (sha2c.containsKey(sha)) {
                if (c.fileExists(s)) {
                    restoreFile(s, c, false);
                } else {
                    System.out.println("File does not exist in that commit.");
                }
            } else {
                System.out.println("No commit with that id exists.");
            }
            /*
             * SHA } catch (NumberFormatException n) {
             * System.out.println("Invalid ID format."); }
             */

        } else {
            System.out.println("Invalid no. of arguments for checkout.");
        }
    }

    /**
     * Restores file to its version in Commit c. Handles merge conflicts for
     * merge as well. Read how to copy files from
     * http://www.javalobby.org/java/forums/t17036.html.
     */
    private void restoreFile(String filename, CommitNode c, boolean isMergeConflict) {
        if (c == null) {
            System.out.println("File does not exist in that commit.");
            return;
        }
        if (!c.fileExists(filename)) {
            System.out.println("File does not exist in that commit.");
            return;
        }
        if (c.folderContainsFile(filename)) {
            String sha = c.getSHA();
            String from = ".gitlet/" + sha + "/" + filename;
            // System.out.println("Getting from: " + from);
            String to = filename;
            if (isMergeConflict) {
                to += ".conflicted";
            }
            /*
             * File sourceFile = new File(from); File destFile = new File(to);
             * FileChannel sourceChannel = null; FileChannel destinationChannel
             * = null; try { sourceChannel = new
             * FileInputStream(sourceFile).getChannel(); destinationChannel =
             * new FileOutputStream(destFile).getChannel();
             * destinationChannel.transferFrom(source, 0, source.size()); }
             * finally { if(source != null) { sourceChannel.close(); }
             * if(destination != null) { destinationChannel.close(); } }
             */
            Path fromPath = Paths.get(from);
            Path toPath = Paths.get(to);
            // System.out.println("Restoring "+filename+" from commit "+sha+"..");
            try {
                Files.copy(fromPath, toPath, REPLACE_EXISTING);
            } catch (IOException ioe) {
                System.out.println("Can't restore file: " + ioe);
            }
        } else {
            /*
             * System.out.println(filename+"Not found originally in"+c.getID());
             * System.out.println(", found in: " +
             * c.whereToFind(filename).getID());
             */
            restoreFile(filename, c.whereToFind(filename), isMergeConflict);
        }
    }

    /** Restores all files to their versions in c. */
    private void restoreAllFiles(CommitNode c) {
        HashSet<String> allFiles = c.getAllFiles();
        for (String filename : allFiles) {
            restoreFile(filename, c, false);
        }
        /*
         * if (f1.isDirectory()) { for (File dirFiles: f1.listFiles()) {
         * restoreAllFiles(dirFiles, c); } } else if (f1.isFile()) { String path
         * = f1.getPath(); String moddedPath = path.substring(2, path.length());
         * if (c.fileExists(moddedPath)) { restoreFile(moddedPath, c, false); }
         * }
         *
         * } /* System.out.println("Restoring all files from "+c+".."); File[]
         * files = new File(".").listFiles(); for (File f : files) { if
         * (f.isFile() && c.fileExists(f.getName())) { restoreFile(f.getName(),
         * c, false); } }
         */
    }

    /**
     * Returns the ID of the split point of branch1 and branch2. Takes a
     * parameter to differentiate for the pull case, where the split point may
     * be non-existent, as compared to a local case, where the split point must
     * be initial commit if nothing else. Redundant now, but there for reference
     * purposes.
     */
    private String splitPoint(String branch1, String branch2, boolean isPull) {
        HashSet<String> s1 = new HashSet<String>();
        // SHA HashSet<Integer> s1 = new HashSet<Integer>();
        HashSet<String> s2 = new HashSet<String>();
        // SHA HashSet<Integer> s2 = new HashSet<Integer>();
        CommitNode c1 = b2h.get(branch1);
        CommitNode c2 = b2h.get(branch2);
        return splitHelper(c1, c2, s1, s2, isPull);
    }

    /** Helper for splitPoint. */
    private String splitHelper(CommitNode c1, CommitNode c2, HashSet<String> s1,
            HashSet<String> s2, boolean isPull) {
        CommitNode newc1 = null, newc2 = null;
        if (c1 == null && c2 == null) {
            if (isPull) {
                return " ";
            }
            return firstSHA;
        } else {
            if (c1 != null) {
                // SHA int i1 = c1.getID();
                String i1 = c1.getSHA();
                if (s2.contains(i1)) {
                    return i1;
                }
                s1.add(i1);
                newc1 = c1.getPrev();
            }
            if (c2 != null) {
                // SHA int i2 = c2.getID();
                String i2 = c2.getSHA();
                if (s1.contains(i2)) {
                    return i2;
                }
                s2.add(i2);
                newc2 = c2.getPrev();
            }
            return splitHelper(newc1, newc2, s1, s2, isPull);
        }
    }

    /** Merges the givenBranch with the current branch. */
    private void merge(String givenBranch) {
        if (!b2h.containsKey(givenBranch)) {
            System.out.println("A branch with that name does not exist.");
        } else if (givenBranch.equals(currName)) {
            System.out.println("Cannot merge a branch with itself.");
        } else {
            // SHA int splitID = splitPoint(currName, givenBranch, false);
            String splitID = splitPoint(currName, givenBranch, false);
            CommitNode splitNode = sha2c.get(splitID);
            CommitNode givenHead = b2h.get(givenBranch);
            // SHA int givenID = givenHead.getID();
            String givenID = givenHead.getSHA();
            // SHA int splitLast = -1, currLast = -1, givenLast = -1;
            HashSet<String> allFiles = head.getAllFiles();
            HashSet<String> givenHeadAll = givenHead.getAllFiles();
            allFiles.addAll(givenHeadAll);
            for (String filename : allFiles) {
                String splitLast = "", currLast = "", givenLast = "";
                // String filename = f.getName();
                if (splitNode.whereToFind(filename) != null) {
                    splitLast = splitNode.whereToFind(filename).getSHA();
                }
                if (head.whereToFind(filename) != null) {
                    currLast = head.whereToFind(filename).getSHA();
                }
                if (givenHead.whereToFind(filename) != null) {
                    givenLast = givenHead.whereToFind(filename).getSHA();
                }
                if (currLast.equals("") && givenLast.equals("")) {
                    int yolo = 0; // do nothing
                } else if (currLast.equals("") && !givenLast.equals("")) {
                    restoreFile(filename, givenHead, false);
                } else if (givenLast.equals("") && !currLast.equals("")) {
                    int yolo = 1;
                    // restoreFile(filename, head, false);
                } else {
                    // System.out.println(filename+":\ncurrLast:
                    // "+currLast+":\ngivenLast: "+givenLast+":\nsplitLast:
                    // "+splitLast);
                    if (!currLast.equals(splitLast) && !givenLast.equals(splitLast)) {
                        // System.out.println(filename+": Merge conflict");
                        restoreFile(filename, givenHead, true);
                    } else if (!currLast.equals(splitLast) && givenLast.equals(splitLast)) {
                        // restoreFile(filename, head, false);
                        int yolo = 1;
                        // System.out.println(filename+" is kept as it is, i.e. from "+currName);
                    } else if (!givenLast.equals(splitLast) && currLast.equals(splitLast)) {
                        // System.out.println(filename+": Restored from "+givenBranch);
                        restoreFile(filename, givenHead, false);
                    }
                }
            }
        }
    }

    /*
     * Returns a stack of CommitNodes from the split point to the current head,
     * both exclusive. isPull parameter taken since we call splitHelper.
     */
    public Stack<CommitNode> splitStack(CommitNode currHead, CommitNode givenHead, boolean isPull) {
        Stack<CommitNode> split2curr = new Stack<CommitNode>();
        HashSet<String> currSet = new HashSet<String>();
        HashSet<String> givenSet = new HashSet<String>();
        String splitSHA = splitHelper(currHead, givenHead, currSet, givenSet, isPull);
        if (sha2c.containsKey(splitSHA)) {
            CommitNode splitNode = sha2c.get(splitSHA);
            return splitStackHelper(currHead, splitNode, split2curr);
        } else {
            return null;
        }
    }

    /** Helper for splitStack. */
    public Stack<CommitNode> splitStackHelper(CommitNode currHead, CommitNode splitNode,
            Stack<CommitNode> split2curr) {
        if (currHead == null) {
            return split2curr;
        }
        split2curr.push(currHead);
        if (currHead.equals(splitNode)) {
            return split2curr;
        }
        return splitStackHelper(currHead.getPrev(), splitNode, split2curr);
    }

    /**
     * Rebases the current branch to the branch <branchName>. Algorithm: 1.
     * Create replayed commit & shift startPoint forward. 2. Create Branch b
     * from commit. (replaying) 3. add as subranch to givenBranch. (replaying)
     * 4. Shift givenBranch to b. (replaying) 5. Repeat until stack is
     * exhausted.
     */
    public void rebaseStack(String branchName, boolean isInteractive) {
        if (!b2h.containsKey(branchName)) {
            System.out.println("A branch with that name does not exist.");
            return;
        }
        if (branchName.equals(currName)) {
            System.out.println("Cannot rebase a branch onto itself.");
            return;
        }
        CommitNode preCurrHead = head;
        CommitNode givenHead = b2h.get(branchName);
        Stack<CommitNode> split2curr = splitStack(head, givenHead, false);
        CommitNode splitNode = split2curr.pop();
        CommitNode startPoint = splitNode;
        CommitNode firstCommit = startPoint; // First initial branchcommit
        Branch givenBranch = h2b.get(givenHead).get(branchName);
        CommitNode newNode = givenHead;
        Branch b = null;
        HashMap<String, Branch> temp = new HashMap<String, Branch>();
        if (givenHead.isInHistory(head)) {
            System.out.println("Already up-to-date.");
            return;
        }
        if (head.isInHistory(givenHead)) {
            head = givenHead;
            if (h2b.containsKey(givenHead)) {
                h2b.get(givenHead).put(currName, givenBranch);
            } else {
                temp.put(currName, givenBranch);
                h2b.put(givenHead, temp);
            }
            b2h.put(currName, givenHead);
            resetSHA(head.getSHA());
            return;
        } // end of special case
        while (!split2curr.empty()) {
            startPoint = split2curr.pop();
            String msg = startPoint.getMsg();
            if (isInteractive) {
                msg = interactive(startPoint, splitNode, preCurrHead);
            }
            temp = new HashMap<String, Branch>();
            if (!msg.equals("")) { // if commit is not being skipped
                newNode = new CommitNode(newNode, currName, msg, startPoint.getNewFiles(),
                        startPoint.getRmFiles(), startPoint, false, false);
                head = newNode;
                sha2c.put(newNode.getSHA(), newNode);
                id2c.put(commitID, newNode);
                b2h.put(currName, newNode);
                temp.put(currName, b);
                h2b.put(newNode, temp);
                b = new Branch(newNode, currName);
                givenBranch.addSubranch(b);
                givenBranch = b;
                commitID += 1;
                if (msg2ids.containsKey(msg)) {
                    msg2ids.get(msg).add(newNode.getSHA());
                } else {
                    HashSet<String> tempSet = new HashSet<String>();
                    tempSet.add(newNode.getSHA());
                    msg2ids.put(msg, tempSet);
                }
            } else { // if commit is being skipped
                newNode = new CommitNode(newNode, currName, msg, startPoint.getNewFiles(),
                        startPoint.getRmFiles(), startPoint, false, true);
            }

        }
        temp = new HashMap<String, Branch>();
        head = newNode;
        sha2c.put(newNode.getSHA(), newNode);
        id2c.put(commitID, newNode);
        b2h.put(currName, newNode);
        temp.put(currName, b);
        h2b.put(newNode, temp);
        resetSHA(head.getSHA());
    }

    /**
     * For interactive rebase prompt, where curr is going to be replayed (or
     * not). Returns "" if commit is being skipped, returns message otherwise.
     */
    private String interactive(CommitNode curr, CommitNode splitNode, CommitNode preCurrHead) {
        String choice = "c";
        String msg = curr.getMsg();
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        try {
            boolean error = false;
            System.out.println("Currently replaying:");
            curr.printInfo();
            do {
                msg = curr.getMsg();
                System.out.println("Would you like to (c)ontinue, (s)kip this commit, "
                                 + "or change this commit's (m)essage?");
                choice = br.readLine();
                if (choice.equals("m")) {
                    System.out.println("Please enter a new message for this commit.");
                    msg = br.readLine();
                }
                if (choice.equals("s")) {
                    error = curr.equals(preCurrHead);
                    if (curr.getPrev() != null) {
                        error = error || curr.getPrev().equals(splitNode);
                    }
                    if (!error) {
                        msg = "";
                    }
                }
            } while (error = true);
            br.close();
        } catch (IOException i) {
            System.out.println("IOException in i-rebase: " + i);
        }
        return msg;
    }

    /** Removes the given branch. */
    private void removeBranch(String branchName) {
        if (branchName.equals(currName)) {
            System.out.println("Cannot remove the current branch.");
        } else if (!b2h.containsKey(branchName)) {
            System.out.println(" A branch with that name does not exist.");
        } else {
            CommitNode reqHead = b2h.get(branchName);
            h2b.get(reqHead).remove(branchName);
            b2h.remove(branchName);
        }
    }

    /** Prints history of commit, up until initial commit. */
    public void log() {
        CommitNode t = head;
        while (t != null) {
            t.printInfo();
            t = t.getPrev();
        }
    }

    /** Prints info of all commits uptil now. */
    public void globalLog() {
        // SHA for (int i=0; i < commitID; i++) {
        for (String i : sha2c.keySet()) {
            sha2c.get(i).printInfo();
        }
    }

    /** Prints the commit IDs of all commits with message <msg>. */
    public void find(String msg) {
        if (!msg2ids.containsKey(msg)) {
            System.out.println("Found no commit with that message.");
        } else {
            // System.out.println(msg+" was found in: ");
            for (String i : msg2ids.get(msg)) { // SHA
                System.out.println(i);
            }
        }
    }

    /** Prints a plethora of information useful for book-keeping. */
    public void status() {
        System.out.println("=== Branches ===");
        System.out.println("*" + currName);
        for (String name : b2h.keySet()) {
            if (!name.equals(currName)) {
                System.out.println(name);
            }
        }
        System.out.println("\n=== Staged Files ===");
        for (String s : stagedFiles) {
            System.out.println(s);
        }
        System.out.println("\n=== Files Marked For Removal ===");
        for (String a : rmFiles) {
            System.out.println(a);
        }
    }

    // ******REMOTE FEATURES*******************

    /** Adds a remote with given information. */
    public void addRemote(String[] args) {
        String remoteName = args[0];
        String login = args[1] + "@" + args[2];
        String location = args[3];
        if (r2login.containsKey(remoteName) || r2loc.containsKey(remoteName)) {
            System.out.println("A remote with that name already exists.");
            return;
        }
        // location = "~/" + location;
        r2login.put(remoteName, login);
        r2loc.put(remoteName, location);
        System.out.println("location:" + r2loc.get(remoteName));
    }

    /** Removes remote of name remoteName. */
    public void removeRemote(String remoteName) {
        if (!r2login.containsKey(remoteName) || !r2loc.containsKey(remoteName)) {
            System.out.println("A remote with that name does not exist.");
            return;
        }
        r2login.remove(remoteName);
        r2loc.remove(remoteName);
    }

    /** Runs a terminal command, using the Process class. */
    public String runCommand(String command) {
        System.out.println("Running command: " + command);
        try {
            // run the Unix "ps -ef" command
            // using the Runtime exec method:
            Process p = Runtime.getRuntime().exec(command);
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            int i = p.waitFor();
            stdInput.close();
            stdError.close();
            return stdError.toString();
        } catch (IOException | InterruptedException e) {
            System.out.println("Exception while running command: " + command + " -- " + e);
            e.printStackTrace();
        }
        return "";
    }

    /**
     * copies remote gitlet onto a directory called .remoteGitlet on local
     * machine, and reads remoteGitlet object, returns it.
     */
    private Gitlet getRemoteGitlet(String remoteName) {
        // runCommand("scp -r " + r2login.get(remoteName) + ":" +
        // r2loc.get(remoteName) + "/.gitlet " + ".remoteGitlet");
        String s = runCommand("scp -r " + r2login.get(remoteName) + ":" + r2loc.get(remoteName)
                + "/. remoteWD");
        Gitlet rmGitlet = null;
        if (new File("remoteWD/.gitlet/savedGitlet.ser").exists()) {
            try {
                FileInputStream fin = new FileInputStream("remoteWD/.gitlet/savedGitlet.ser");
                ObjectInputStream ois = new ObjectInputStream(fin);
                Object historyObject = ois.readObject();
                rmGitlet = (Gitlet) historyObject;
                fin.close();
                ois.close();
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Could not read remote gitlet state!");
                System.out.println(e);
            }
        }
        return rmGitlet;
    }

    /** Pushes new commits of the given branch onto the given remote. */
    public void push(String[] args) {
        String remoteName = args[0];
        String branchName = args[1];
        if (!r2login.containsKey(remoteName)) {
            System.out.println("No such remote.");
            return;
        }
        Gitlet remote = getRemoteGitlet(remoteName);
        if (remote == null) { // i.e. there's no Gitlet state on the remote
            String s = runCommand("scp -r .gitlet/. " + r2login.get(remoteName) + ":"
                    + r2loc.get(remoteName) + "/.gitlet");
            remote = this; // copying gitlet state
            recursiveDelete(new File("./remoteWD"));
            return;
        } else { // if there is a remote Gitlet state
            if (!this.containsBranch(branchName)) {
                System.out.println("Local Gitlet does not have that branch.");
                return;
            }
            if (!remote.containsBranch(branchName)) {
                remote.branch(branchName);
            }
            CommitNode remoteHead = remote.getBranchHead(branchName);
            CommitNode localHead = this.getBranchHead(branchName);
            System.out.println("remotehead: " + remoteHead.getSHA());
            System.out.println("localhead: " + localHead.getSHA());
            if (!remoteHead.isInHistory(localHead)) {
                System.out.println("Please pull down remote changes before pushing.");
                return;
            } else {
                CommitNode startPoint = localHead;
                while (!startPoint.equals(remoteHead)) {
                    startPoint = startPoint.getPrev();
                }
                while (!remoteHead.equals(localHead)) {
                    startPoint = startPoint.next(branchName);
                    remoteHead = remote.commitToBranch(branchName, remoteHead, startPoint, true);
                }
                try {
                    File saveGitlet = new File("remoteWD/.gitlet/savedGitlet.ser");
                    FileOutputStream fileOut = new FileOutputStream(saveGitlet);
                    ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
                    objectOut.writeObject(remote);
                    objectOut.close();
                    fileOut.close();
                } catch (IOException e) {
                    String msg = "IOException while saving remote gitlet state!";
                    System.out.println(msg);
                }
                String a = runCommand("scp -r remoteWD/. " + r2login.get(remoteName) + ":"
                        + r2loc.get(remoteName));
            }
        }

    }

    /** Pulls new commits of the given branch from the given remote. */
    public void pull(String[] args) {
        String remoteName = args[0];
        String branchName = args[1];
        Gitlet remote = getRemoteGitlet(remoteName);
        System.out.println(remote == null);
        if (!r2login.containsKey(remoteName) || remote == null) {
            System.out.println("No such remote.");
            return;
        }
        if (!remote.containsBranch(branchName)) {
            System.out.println("That remote does not have that branch.");
            return;
        }
        if (!this.containsBranch(branchName)) {
            System.out.println("Local Gitlet does not have that branch.");
            return;
        }
        CommitNode remoteHead = remote.getBranchHead(branchName);
        CommitNode localHead = this.getBranchHead(branchName);
        if (remoteHead.isInHistory(localHead) || remoteHead.equals(localHead)) {
            System.out.println("Already up-to-date.");
            return;
        }
        if (localHead.isInHistory(remoteHead)) {
            CommitNode startPoint = remoteHead;
            while (!startPoint.equals(localHead)) {
                startPoint = startPoint.getPrev();
            }
            // System.out.println("Startpoint = localHead now.");
            while (!localHead.equals(remoteHead)) {
                startPoint = startPoint.next(branchName);
                localHead = this.commitToBranch(branchName, localHead, startPoint, false);
            }
            // System.out.println("LocalHead = RemoteHead now.");
            this.resetSHA(localHead.getSHA());
            // System.out.println("Resetted to local head.");
            return;
        }
        // System.out.println("Neither heads are in each others history.");
        CommitNode origLocal = localHead;
        Stack<CommitNode> split2curr = splitStack(remoteHead, localHead, true);
        if (split2curr != null) {
            CommitNode splitNode = split2curr.pop();
            if (splitNode != null) {
                CommitNode startPoint = splitNode;
                while (!split2curr.empty()) {
                    startPoint = split2curr.pop();
                    if (startPoint != null) {
                        localHead = this.commitToBranch(branchName, localHead, startPoint, false);
                    }
                }
                CommitNode changes = new CommitNode(localHead, branchName, origLocal.getMsg(),
                        origLocal.getNewFiles(), origLocal.getRmFiles(), null, false, false);
                localHead = this.commitToBranch(branchName, localHead, changes, false);
                this.resetSHA(localHead.getSHA());
            }
        }
        recursiveDelete(new File("./remoteWD"));
    }

    /**
     * Clones the Gitlet state into a folder called remoteName. Also copies
     * snapshot of latest commits.
     */
    public void clone(String remoteName) {
        if (!r2login.containsKey(remoteName)) {
            System.out.println("No such remote.");
            return;
        }
        Gitlet remote = getRemoteGitlet(remoteName);
        String a = runCommand("mkdir " + remoteName);
        String b = runCommand("cp -r remoteWD/.gitlet/. " + remoteName + "/.gitlet");
        if (remote != null) {
            CommitNode remoteHead = remote.getHead();
            String remoteSHA = remoteHead.getSHA();
            String dirPath = "remoteWD/.gitlet/" + remoteSHA;
            File dir = new File(dirPath);
            for (File f : dir.listFiles()) {
                runCommand("cp " + dirPath + "/" + f.getName() + " " + remoteName + "/"
                        + f.getName());
            }
        }
        recursiveDelete(new File("./remoteWD"));

    }

    /**
     * Returns true if THIS Gitlet contains branch with name branchName.
     */
    public boolean containsBranch(String branchName) {
        return this.b2h.containsKey(branchName);
    }

    /**
     * Returns the next commit node in THIS gitlet with the branchname. This is
     * specially useful for the special case of pull, where you need the next
     * commit from the remote gitlet. Note that the sha remains the same
     * throughout, and you use this sha in sha2c to get the commmitNode within a
     * specific gitlet.
     */
    public CommitNode nextInBranch(String sha, String branchName) {
        if (sha2c.containsKey(sha)) {
            return sha2c.get(sha).next(branchName);
        }
        return null;
    }

    /**
     * Add CommitNode c to branchname, whose current head in THIS Gitlet is
     * currHead.
     */
    public CommitNode commitToBranch(String branchName, CommitNode currHead, CommitNode c,
            boolean isRemote) {
        try {
            c.makeFolder(isRemote);
        } catch (IOException e) {
            System.out.println("Commit exception " + e);
        }
        Branch b = new Branch(c, branchName);
        Branch givenBranch = h2b.get(currHead).get(branchName);
        givenBranch.addSubranch(b);
        givenBranch = b;
        b2h.put(branchName, c);
        if (h2b.containsKey(c)) {
            h2b.get(c).put(branchName, b);
        } else {
            HashMap<String, Branch> temp = new HashMap<String, Branch>();
            temp.put(branchName, b);
            h2b.put(c, temp);
        }
        String sha = c.getSHA(); // SHA
        sha2c.put(sha, c); // SHA
        id2c.put(commitID, c);
        // c.setID(this.commitID);
        this.commitID += 1;
        String msg = c.getMsg();
        if (msg2ids.containsKey(msg)) {
            msg2ids.get(msg).add(sha);
        } else {
            HashSet<String> tempSet = new HashSet<String>(); // SHA
            tempSet.add(sha);
            msg2ids.put(msg, tempSet);
        }
        this.head = c;
        return c;

    }

    /** Gets head of branch in this Gitlet. */
    public CommitNode getBranchHead(String branchName) {
        return this.b2h.get(branchName);
    }

    /** Sets head of branch in this Gitlet. */
    public void setBranchHead(String branchName, CommitNode c) {
        this.b2h.put(branchName, c);
    }

    /** Gets the head of the current branch of THIS Gitlet. */
    public CommitNode getHead() {
        return this.head;
    }

    /** Recursive delete, taken from GitletPublicTest. */
    private static void recursiveDelete(File d) {
        if (d.isDirectory()) {
            for (File f : d.listFiles()) {
                recursiveDelete(f);
            }
        }
        d.delete();
    }

    // ~~~~~~~~~~~~~~~END OF REMOTE FEATURES

    /**
     * Depending on user input for dangerous commands, returns true if Gitlet
     * should proceed with the given command.
     */
    public boolean shouldProceed(String command) {
        InputStreamReader isr = new InputStreamReader(System.in);
        BufferedReader br = new BufferedReader(isr);
        String c = "no";
        dangerousMethods = new HashSet<String>();
        dangerousMethods.add("checkout");
        dangerousMethods.add("reset");
        dangerousMethods.add("merge");
        dangerousMethods.add("rebase");
        dangerousMethods.add("i-rebase");
        dangerousMethods.add("pull");
        if (dangerousMethods.contains(command)) {
            System.out.println("Warning: The command you entered may alter the files in your"
                    + " working directory. Uncommitted changes may be lost."
                    + " Are you sure you want to continue? (yes/no)");
            try {
                c = br.readLine();
                br.close();
                isr.close();
            } catch (IOException ioe) {
                System.out.println("Couldn't input command: " + ioe);
            }
        } else {
            c = "yes";
        }
        return c.equals("yes");
    }

    /** Cleans up the stuff created during certain remote operations. */
    public void cleanUp(String command) {
        switch (command) {
            case "pull":
            case "push":
            case "clone":
                recursiveDelete(new File("./remoteWD"));
                break;
            default:
                boolean a = true; // do nothing
        }
    }

    /** Returns the length of the parameters if the commit command is called. */
    public static boolean paramLengthCommit(String[] param) {
        if (param.length < 1) {
            System.out.println("Please enter a commit message.");
        }
        return param.length == 1;
    }

    /** Main function, dispatches tasks accordingly. */
    public static void main(String[] args) {
        Gitlet g = new Gitlet();
        if (Gitlet.readSaved() != null) {
            g = Gitlet.readSaved();
        }
        String command = args[0];
        String[] param = new String[args.length - 1];
        System.arraycopy(args, 1, param, 0, args.length - 1);
        if (g.shouldProceed(command)) {
            switch (command) {
                case "init":
                    g.initialize();
                    break;
                case "add":
                    g.addFile(param[0]);
                    break;
                case "commit":
                    if (paramLengthCommit(param)) {
                        g.commit(param[0], false);
                    }
                    break;
                case "rm":
                    g.removeFile(param[0]);
                    break;
                case "log":
                    g.log();
                    break;
                case "global-log":
                    g.globalLog();
                    break;
                case "find":
                    g.find(param[0]);
                    break;
                case "status":
                    g.status();
                    break;
                case "checkout":
                    g.checkout(param);
                    break;
                case "branch":
                    g.branch(param[0]);
                    break;
                case "rm-branch":
                    g.removeBranch(param[0]);
                    break;
                case "reset":
                    g.resetSHA(param[0]); // SHA
                    break;
                case "merge":
                    g.merge(param[0]);
                    break;
                case "rebase":
                    g.rebaseStack(param[0], false);
                    break;
                case "i-rebase":
                    g.rebaseStack(param[0], true);
                    break;
                case "add-remote":
                    g.addRemote(param);
                    break;
                case "rm-remote":
                    g.removeRemote(param[0]);
                    break;
                case "pull":
                    g.pull(param);
                    break;
                case "push":
                    g.push(param);
                    break;
                case "clone":
                    g.clone(param[0]);
                    break;
                default:
                    System.out.println("Invalid command.");
            }
        }
        g.cleanUp(command);
        Gitlet.save(g);
    }
}
