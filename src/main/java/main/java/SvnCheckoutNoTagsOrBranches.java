package main.java;

import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class SvnCheckoutNoTagsOrBranches {
    public static void main(String... args) throws SVNException {
        String checkoutPath = null;
        String username = null;
        String password = null;
        String checkoutRootPath = new File("").getAbsolutePath();

        if (args.length > 2) {
            checkoutPath = args[0].trim();
            username = args[1].trim();
            password = args[2].trim();

            if (args.length == 4)
                checkoutRootPath = args[3].trim();
        } else {
            System.out.println("Usage: java -jar svntrunks.jar URL USERNAME PASSWORD [PATH]\n");
            System.out.println("URL:        The mandatory path to the SVN reposatory");
            System.out.println("USERNAME:   The username to log in as");
            System.out.println("PASSWORD:   Password for the username");
            System.out.println("[PATH]      Optional destination folder (defaults to current)\n\n");
            System.out.println("Example:   java -jar svntrunks.jar https://svn.java.net/svn/jersey~svn david secret jersey-checkout");
            System.exit(1);
        }

        DAVRepositoryFactory.setup();

        final SVNRepository repository = SVNRepositoryFactory.create(SVNURL.parseURIDecoded(checkoutPath));
        repository.setAuthenticationManager(SVNWCUtil.createDefaultAuthenticationManager(username, password));

        final SVNClientManager clientManager = SVNClientManager.newInstance(null, repository.getAuthenticationManager());
        final SVNUpdateClient updateClient = clientManager.getUpdateClient();
        updateClient.setIgnoreExternals(false);

        final SVNNodeKind nodeKind = repository.checkPath("", -1);

        if (nodeKind == SVNNodeKind.NONE) {
            System.err.println("There is no entry at '" + checkoutPath + "'.");
            System.exit(1);
        } else if (nodeKind == SVNNodeKind.FILE) {
            System.err.println("The entry at '" + checkoutPath + "' is a file while a directory was expected.");
            System.exit(1);
        }

        System.out.println("Checkout source: " + checkoutPath);
        System.out.println("Checkout destination: " + checkoutRootPath);
        recursive(updateClient, repository, checkoutPath, checkoutRootPath, "");
        System.out.println("Repository latest revision: " + repository.getLatestRevision());
    }

    private static void recursive(SVNUpdateClient updateClient, SVNRepository repository, String checkoutRootPath, String destRootPath, String repoPath) throws SVNException {
        Collection<SVNDirEntry> entries = repository.getDir(repoPath, -1, null, (Collection) null);
        if (dirContainsTrunkBranchesOrTagsSubfolder(entries)) {
            List<SVNDirEntry> dir2CheckoutOrUpdate = entries.stream().filter((svnDirEntry) -> !isBranchesOrTagsFolder(svnDirEntry)).collect(Collectors.toList());
            for (SVNDirEntry svnDirEntry : dir2CheckoutOrUpdate) {
                String url = checkoutRootPath + "/" + repoPath + "/" + svnDirEntry.getName();
                File dstPath = new File(destRootPath + (!repoPath.isEmpty() ? "/" : "") + repoPath + "/" + svnDirEntry.getName());
                if (!dstPath.exists()) {
                    long revision = updateClient.doCheckout(
                            SVNURL.parseURIEncoded(url),
                            dstPath,
                            SVNRevision.UNDEFINED,
                            SVNRevision.HEAD,
                            SVNDepth.INFINITY,
                            false);
                    System.out.println("checkout: " + repoPath + " to revision @" + revision);
                } else {
                    long revision = updateClient.doUpdate(
                            dstPath,
                            SVNRevision.HEAD,
                            SVNDepth.INFINITY,
                            true,
                            false);
                    System.out.println("update  : " + repoPath + " to revision @" + revision);
                }
            }
        } else {
            for (SVNDirEntry entry : entries) {
                if (!isBranchesOrTagsFolder(entry) && entry.getKind() == SVNNodeKind.DIR) {
                    recursive(
                            updateClient,
                            repository,
                            checkoutRootPath,
                            destRootPath,
                            (repoPath.equals("")) ? entry.getName() : repoPath + "/" + entry.getName());
                }
            }
        }
    }

    private static boolean dirContainsTrunkBranchesOrTagsSubfolder(Collection<SVNDirEntry> entries) {
        return entries.stream().anyMatch(SvnCheckoutNoTagsOrBranches::isBranchesOrTagsFolder) || dirHasTrunkFolder(entries);
    }

    private static boolean dirHasTrunkFolder(Collection<SVNDirEntry> entries) {
        return entries.stream().anyMatch(entry -> entry.getName().equalsIgnoreCase("trunk"));
    }

    private static Boolean isBranchesOrTagsFolder(SVNDirEntry entry) {
        return entry.getName().equalsIgnoreCase("branches") || entry.getName().equalsIgnoreCase("tags");
    }
}