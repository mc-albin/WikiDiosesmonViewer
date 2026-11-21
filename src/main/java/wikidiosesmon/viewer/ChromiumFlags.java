package wikidiosesmon.viewer;

import org.cef.CefApp;
import org.cef.callback.CefCommandLine;
import org.cef.callback.CefSchemeRegistrar;
import org.cef.handler.CefAppHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Passes extra switches to Chromium before MCEF starts it (MCEF starts it at the title screen,
 * after mods are initialised). Only the command line is touched; message pumping stays with MCEF,
 * which runs it once per frame on the render thread.
 */
final class ChromiumFlags implements CefAppHandler {
    private static final Logger LOG = LoggerFactory.getLogger("WikiDiosesmonViewer");
    private final List<String> flags;

    private ChromiumFlags(List<String> flags) {
        this.flags = flags;
    }

    static void install(List<String> configured) {
        List<String> flags = new ArrayList<>(configured);
        if (!flags.isEmpty() || !System.getProperty("wdv.flags", "").isBlank()) {
            // A handler replaces MCEF's start-up switches; keep the harmless one.
            flags.add(0, "autoplay-policy=no-user-gesture-required");
        }
        String extra = System.getProperty("wdv.flags", "");
        for (String f : extra.split(",")) {
            if (!f.isBlank()) {
                flags.add(f.strip());
            }
        }
        if (flags.isEmpty()) {
            return;
        }
        try {
            CefApp.addAppHandler(new ChromiumFlags(flags));
            LOG.info("Opciones de Chromium: {}", flags);
        } catch (IllegalStateException | LinkageError e) {
            LOG.warn("No se pudieron aplicar las opciones de Chromium: {}", e.toString());
        }
    }

    @Override
    public void onBeforeCommandLineProcessing(String processType, CefCommandLine commandLine) {
        if (processType != null && !processType.isEmpty()) {
            return;
        }
        for (String f : flags) {
            String s = f.startsWith("--") ? f.substring(2) : f;
            int eq = s.indexOf('=');
            if (eq > 0) {
                commandLine.appendSwitchWithValue(s.substring(0, eq), s.substring(eq + 1));
            } else {
                commandLine.appendSwitch(s);
            }
        }
    }

    @Override
    public boolean onBeforeTerminate() {
        return false;
    }

    @Override
    public void stateHasChanged(CefApp.CefAppState state) {
    }

    @Override
    public void onRegisterCustomSchemes(CefSchemeRegistrar registrar) {
    }

    @Override
    public void onContextInitialized() {
    }

    @Override
    public void onScheduleMessagePumpWork(long delayMs) {
        // MCEF already pumps Chromium every frame on the render thread; pumping from here too
        // would run it on another thread.
    }
}
