package the.bytecode.club.bytecodeviewer.plugin.preinstalled;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import the.bytecode.club.bytecodeviewer.BytecodeViewer;
import the.bytecode.club.bytecodeviewer.Configuration;
import the.bytecode.club.bytecodeviewer.api.Plugin;
import the.bytecode.club.bytecodeviewer.api.PluginConsole;
import the.bytecode.club.bytecodeviewer.resources.ResourceContainer;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Plugin to export only modified classes.
 * Usage:
 * 1. Run the plugin right after opening the workspace (captures initial state)
 * 2. Modify the desired classes
 * 3. Save the whole workspace (Ctrl+S or File -> Save As...)
 * 4. Run the plugin again: it will create a ZIP file with only the modified classes
 *    in the same directory where you last saved a file
 * @author DDDrag0
 */
public class ExportModifiedOnly extends Plugin {

    // Map to store initial state (class name -> bytecode)
    private static final Map<String, byte[]> originalBytes = new HashMap<>();
    private static boolean firstRun = true;
    private static PluginConsole console;

    @Override
    public void execute(List<ClassNode> classNodes) {
        // Initialize console if it doesn't exist
        if (console == null) {
            console = new PluginConsole("Export Modified Only");
        }
        if (firstRun) {
            // Save initial state of ALL loaded classes
            captureInitialState();
            console.appendText("=== INITIAL STATE CAPTURED ===\n");
            console.appendText("Monitored classes: " + originalBytes.size() + "\n");
            console.appendText("Now you can modify the classes.\n");
            console.appendText("After modifications, save the whole workspace (Ctrl+S or File -> Save As...).\n");
            console.appendText("Then run this plugin again to export only the modified classes.\n");
            console.appendText("The ZIP file will be saved in the same directory as your last saved file.\n");
            console.setVisible(true);
            firstRun = false;
            return;
        }
        // Compare current state with initial state
        List<String> modifiedClasses = new ArrayList<>();
        for (ResourceContainer container : BytecodeViewer.getResourceContainers()) {
            for (Map.Entry<String, ClassNode> entry : container.resourceClasses.entrySet()) {
                String className = entry.getKey(); // name without .class
                ClassNode currentCN = entry.getValue();
                // Rebuild current bytecode
                byte[] currentBytes = getBytesFromClassNode(currentCN);
                // Retrieve original bytecode
                byte[] original = originalBytes.get(className);
                if (original == null) {
                    // New class (added after first run) – consider it modified
                    modifiedClasses.add(className);
                } else if (!Arrays.equals(currentBytes, original)) {
                    modifiedClasses.add(className);
                }
            }
        }
        if (modifiedClasses.isEmpty()) {
            console.appendText("No modified classes detected.\n");
        } else {
            // Get the last saved file's directory
            File lastSaved = Configuration.getLastSaveDirectory();
            File saveDir;
            // If it's a file, use its parent directory
            if (lastSaved.isFile()) {
                saveDir = lastSaved.getParentFile();
            } else {
                saveDir = lastSaved;
            }
            // Ensure the directory exists
            if (saveDir != null && !saveDir.exists()) {
                saveDir.mkdirs();
            }
            // Fallback to user home if directory is invalid
            if (saveDir == null || !saveDir.exists() || !saveDir.isDirectory()) {
                saveDir = new File(System.getProperty("user.home"));
                console.appendText("Warning: Using fallback directory: " + saveDir.getAbsolutePath() + "\n");
            }
            // Generate filename with timestamp
            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            File zipFile = new File(saveDir, "modified_classes_" + timestamp + ".zip");
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
                for (ResourceContainer container : BytecodeViewer.getResourceContainers()) {
                    for (String className : modifiedClasses) {
                        ClassNode cn = container.resourceClasses.get(className);
                        if (cn != null) {
                            // Preserve package structure
                            String entryName = className.replace('.', '/') + ".class";
                            zos.putNextEntry(new ZipEntry(entryName));
                            zos.write(getBytesFromClassNode(cn));
                            zos.closeEntry();
                        }
                    }
                }
                console.appendText("Exported " + modifiedClasses.size() + " classes to:\n" + zipFile.getAbsolutePath() + "\n");
                console.appendText("List of modified classes:\n");
                for (String cls : modifiedClasses) {
                    console.appendText("  - " + cls + "\n");
                }
            } catch (Exception e) {
                BytecodeViewer.handleException(e);
            }
        }
        console.setVisible(true);
    }

    //Captures the initial state of all loaded classes.
    private void captureInitialState() {
        originalBytes.clear();
        for (ResourceContainer container : BytecodeViewer.getResourceContainers()) {
            for (Map.Entry<String, ClassNode> entry : container.resourceClasses.entrySet()) {
                String className = entry.getKey();
                ClassNode cn = entry.getValue();
                originalBytes.put(className, getBytesFromClassNode(cn));
            }
        }
    }

    //Converts a ClassNode to a byte array (using ClassWriter).
    private byte[] getBytesFromClassNode(ClassNode cn) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }
}
