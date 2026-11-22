package wikidiosesmon.viewer;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gives Chromium's helper processes (page, drawing, network) "below normal" CPU priority on
 * Windows. They draw the page on the CPU, and the animated sprites keep them busy all the time.
 * At normal priority they competed with Minecraft for the same cores (stutter, FPS drops). Now the
 * game always comes first and the page only uses the CPU time the game leaves free. Nothing changes
 * on screen unless the CPU is completely full.
 */
final class ChromiumPriority {
    private static final Logger LOG = LoggerFactory.getLogger("WikiDiosesmonViewer");
    private static final int PROCESS_SET_INFORMATION = 0x0200;
    private static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;
    private static final int BELOW_NORMAL_PRIORITY_CLASS = 0x4000;
    /** Ticks between checks (Chromium may start a new page process when you open a link). */
    private static final int INTERVAL_TICKS = 100;

    private interface Kernel32 extends Library {
        Pointer OpenProcess(int access, boolean inherit, int pid);

        boolean SetPriorityClass(Pointer process, int priorityClass);

        int GetPriorityClass(Pointer process);

        boolean CloseHandle(Pointer handle);
    }

    private static final Set<Long> DONE = new HashSet<>();
    /** Processes whose priority was read back as "below normal". */
    private static int lowered;
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static Kernel32 kernel32;
    private static boolean failed;
    private static int ticks;

    private ChromiumPriority() {
    }

    /** Client tick: every few seconds, lowers the priority of new Chromium processes off-thread. */
    static void tick() {
        if (failed || !ViewerConfig.get().chromiumPrioridadBaja || !WebView.runtimeReady()
                || Util.getOperatingSystem() != Util.OperatingSystem.WINDOWS) {
            return;
        }
        if (++ticks < INTERVAL_TICKS) {
            return;
        }
        ticks = 0;
        if (RUNNING.compareAndSet(false, true)) {
            Util.getIoWorkerExecutor().execute(() -> {
                try {
                    lowerAll();
                } finally {
                    RUNNING.set(false);
                }
            });
        }
    }

    private static void lowerAll() {
        try {
            if (kernel32 == null) {
                kernel32 = Native.load("kernel32", Kernel32.class);
            }
            ProcessHandle.current().descendants()
                    .filter(p -> p.info().command().map(c -> c.toLowerCase().endsWith("jcef_helper.exe")).orElse(false))
                    .forEach(p -> {
                        synchronized (DONE) {
                            if (!DONE.add(p.pid())) {
                                return;
                            }
                        }
                        Pointer h = kernel32.OpenProcess(PROCESS_SET_INFORMATION | PROCESS_QUERY_LIMITED_INFORMATION, false, (int) p.pid());
                        if (h != null) {
                            try {
                                kernel32.SetPriorityClass(h, BELOW_NORMAL_PRIORITY_CLASS);
                                if (kernel32.GetPriorityClass(h) == BELOW_NORMAL_PRIORITY_CLASS) {
                                    synchronized (DONE) {
                                        lowered++;
                                    }
                                }
                            } finally {
                                kernel32.CloseHandle(h);
                            }
                        }
                    });
        } catch (Throwable t) {
            failed = true;
            LOG.warn("No se pudo bajar la prioridad de Chromium: {}", t.toString());
        }
    }

    /** For the development autotest: Chromium processes confirmed at "below normal" so far. */
    static int count() {
        synchronized (DONE) {
            return lowered;
        }
    }
}
