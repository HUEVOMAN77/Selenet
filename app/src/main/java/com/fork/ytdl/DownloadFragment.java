package com.fork.ytdl;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.fork.ytdl.databinding.FragmentDownloadBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadFragment extends Fragment implements DownloadService.Listener {

    private FragmentDownloadBinding binding;
    private final QueueAdapter adapter = new QueueAdapter();
    private final Handler main = new Handler(Looper.getMainLooper());
    private static final ExecutorService THREADS = Executors.newSingleThreadExecutor();
    /** Margen que se pide ademas del archivo: la bajada escribe y despues publica. */
    private static final long MARGIN = 80L * 1024 * 1024;
    private static String asked;
    private boolean reading;
    private CoachMarks coach;
    /** La pantalla de busqueda devuelve el link elegido; se consume al volver. */
    private final ActivityResultLauncher<Intent> searcher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), this::picked);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle saved) {
        binding = FragmentDownloadBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        binding.queueList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.queueList.setAdapter(adapter);

        binding.btnFmtAuto.setChecked(true);

        binding.btnAdd.setOnClickListener(v -> add());
        binding.btnSearchVideo.setOnClickListener(v -> searcher.launch(
                new Intent(requireContext(), SearchActivity.class)));
        binding.btnClearPart.setOnClickListener(v -> clearParts());
        binding.btnCopyLog.setOnClickListener(v -> copyLog());
        binding.btnSearch.setOnClickListener(v -> toggleSearch());
        binding.filterInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void afterTextChanged(android.text.Editable s) {
                adapter.setQuery(s == null ? "" : s.toString());
                refresh();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        MainActivity activity = (MainActivity) requireActivity();
        String pending = activity.takePendingUrl();
        if (pending != null) {
            binding.urlInput.setText(pending);
        } else {
            offerClipboard();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        DownloadService.addListener(this);
        binding.logCard.setVisibility(
                Storage.logVisible(requireContext()) ? View.VISIBLE : View.GONE);
        refresh();
        showCoach();
    }

    @Override
    public void onPause() {
        DownloadService.removeListener(this);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (coach != null) {
            coach.close();
            coach = null;
        }
        binding = null;
        super.onDestroyView();
    }

    /**
     * Las visitas solo salen una vez, justo despues de la guia de inicio, y con el layout
     * ya medido: si no, el hueco del spotlight saldria con ancho 0.
     */
    private void showCoach() {
        if (coach != null || binding == null || !FirstRun.needsCoach(requireContext())) {
            return;
        }
        final Context app = requireContext().getApplicationContext();
        binding.btnAdd.post(() -> {
            if (binding == null || !isAdded() || coach != null) {
                return;
            }
            coach = CoachMarks.show(requireActivity(), new CoachMarks.Step[]{
                    new CoachMarks.Step(binding.urlInput,
                            R.string.coach_url_t, R.string.coach_url_s),
                    new CoachMarks.Step(binding.fmtToggleGroup,
                            R.string.coach_fmt_t, R.string.coach_fmt_s),
                    new CoachMarks.Step(binding.btnAdd,
                            R.string.coach_add_t, R.string.coach_add_s)}, () -> {
                coach = null;
                FirstRun.coachDone(app);
            });
        });
    }

    @Override
    public void onQueueChanged() {
        if (binding != null) {
            refresh();
        }
    }

    private void add() {
        String typed = binding.urlInput.getText() == null
                ? "" : binding.urlInput.getText().toString().trim();
        String url = firstUrl(typed);
        if (url == null) {
            Toast.makeText(requireContext(), R.string.link_invalido, Toast.LENGTH_SHORT).show();
            return;
        }
        if (reading) {
            return;
        }
        java.util.List<String> batch = allUrls(typed);
        if (batch.size() > 1) {
            // Nadie escribe dos links de a uno: si hay varios en el texto, se ofrecen todos.
            askBatch(batch);
            return;
        }
        if (looksLikeList(url)) {
            // "list=" y /playlist pueden venir pegados a un video suelto: hay que preguntar.
            probeList(url);
            return;
        }
        if (binding.btnFmtMp4.isChecked()) {
            // la resolucion elegida en Ajustes; si esta guardado "Solo audio" se usa la mejor
            Context context = requireContext();
            String spec = Quality.savedVideo(context);
            enqueue(url, spec, Quality.label(context, spec));
            return;
        }
        if (binding.btnFmtMp3.isChecked()) {
            // la calidad de audio elegida en Ajustes (m4a al final, siempre sin merge)
            Context context = requireContext();
            String spec = Quality.savedAudio(context);
            enqueue(url, spec, Quality.savedAudioLabel(context));
            return;
        }
        readFormats(url);
    }

    /**
     * Vuelve de la pantalla de busqueda. Con el toque se rellena el campo y se corre el
     * flujo normal (Auto lee calidades, MP4/MP3 usa la guardada, lista pregunta); con la
     * pulsacion larga se agrega directo con la calidad guardada, sin preguntas.
     */
    private void picked(ActivityResult result) {
        if (binding == null || !isAdded()) {
            return;
        }
        Intent data = result.getData();
        if (result.getResultCode() != android.app.Activity.RESULT_OK || data == null) {
            return;
        }
        String url = data.getStringExtra(SearchActivity.EXTRA_URL);
        if (url == null) {
            return;
        }
        if (data.getBooleanExtra(SearchActivity.EXTRA_DIRECT, false)) {
            Context context = requireContext();
            String[] fmt = savedQuality(context);
            enqueue(url, fmt[0], fmt[1]);
            return;
        }
        binding.urlInput.setText(url);
        add();
    }

    /** Lee los formatos del motor y muestra los que se pueden bajar sin merge. */
    private void readFormats(String url) {
        reading = true;
        Context app = requireContext().getApplicationContext();
        binding.btnAdd.setEnabled(false);
        setStatus(app.getString(R.string.fmt_obteniendo), true);
        THREADS.execute(() -> {
            Formats.Result result;
            try {
                Engine.init(app);
                result = Formats.parse(Engine.formats(url));
            } catch (Exception e) {
                Logs.append(null, "formatos: " + e);
                result = Formats.Result.fail("error");
            }
            final Formats.Result found = result;
            main.post(() -> {
                reading = false;
                if (binding == null || !isAdded() || getContext() == null) {
                    return;
                }
                binding.btnAdd.setEnabled(true);
                setStatus("", false);
                if (found.ok) {
                    showQualities(url, found);
                } else {
                    Context context = requireContext();
                    Toast.makeText(context, getString(R.string.fmt_fallo,
                                    Errors.reason(context, found.errorKind, null)),
                            Toast.LENGTH_LONG).show();
                    enqueue(url, Quality.saved(context), Quality.savedLabel(context));
                }
            });
        });
    }

    private void showQualities(String url, Formats.Result result) {
        Context context = requireContext();
        String[] labels = new String[result.options.size()];
        String saved = Quality.saved(context);
        int savedHeight = Quality.heightOf(saved);
        for (int i = 0; i < labels.length; i++) {
            Formats.Option option = result.options.get(i);
            boolean remembered = option.id.equals(saved)
                    || (savedHeight > 0 && option.height == savedHeight && !option.needsMerge());
            labels[i] = option.label(context) + (remembered ? "  ✓" : "");
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.fmt_titulo)
                .setMessage(header(context, result))
                .setItems(labels, (dialog, which) -> choose(url, result.options.get(which)))
                .setNegativeButton(R.string.q_usar_ultima,
                        (dialog, which) -> enqueue(url, Quality.saved(context),
                                Quality.savedLabel(context)))
                .show();
    }

    /** Todos los links del texto, sin repetir, con un techo para pegadas absurdas. */
    private static java.util.List<String> allUrls(String text) {
        java.util.List<String> out = new java.util.ArrayList<>();
        java.util.regex.Matcher matcher = HTTP.matcher(text == null ? "" : text);
        while (matcher.find() && out.size() < 20) {
            String url = matcher.group();
            if (!out.contains(url)) {
                out.add(url);
            }
        }
        return out;
    }

    private void askBatch(final java.util.List<String> urls) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.multilink_titulo)
                .setMessage(getString(R.string.multilink_mensaje, urls.size()))
                .setPositiveButton(getString(R.string.multilink_todos, urls.size()),
                        (dialog, which) -> enqueueAll(urls))
                .setNegativeButton(R.string.multilink_unico, (dialog, which) -> {
                    binding.urlInput.setText(urls.get(0));
                    add();
                })
                .setNeutralButton(R.string.clipboard_descartar, null)
                .show();
    }

    /**
     * Varios links entran con la calidad que ya esta guardada en Ajustes: leer los
     * formatos de cada video uno por uno antes de empezar seria media hora de espera,
     * y el que pega una lista lo que quiere es que bajen. Con "Solo audio" elegido se
     * usa la calidad de audio, igual que con un link suelto.
     */
    private void enqueueAll(java.util.List<String> urls) {
        Context context = requireContext();
        String[] fmt = savedQuality(context);
        for (String url : urls) {
            DownloadService.enqueue(context, url, fmt[0], fmt[1]);
        }
        binding.urlInput.setText("");
        Toast.makeText(context, getString(R.string.multilink_ok, urls.size()),
                Toast.LENGTH_SHORT).show();
        scrollToNew();
    }

    /** fmt[0] = especificacion para el motor, fmt[1] = etiqueta corta para la fila. */
    private String[] savedQuality(Context context) {
        if (binding.btnFmtMp3.isChecked()) {
            return new String[]{Quality.savedAudio(context),
                    Quality.savedAudioLabel(context)};
        }
        String spec = Quality.savedVideo(context);
        return new String[]{spec, Quality.label(context, spec)};
    }

    // ------------------------------------------------------------------ listas

    /** Basta la forma del link: si podria ser una lista se pregunta, sin leer el motor. */
    private static final java.util.regex.Pattern LIST_HINT =
            java.util.regex.Pattern.compile("(?i)([?&]list=|/playlist|/album/|/sets/)"
                    + "|youtube\\.com/(feed/playlists|@[^/]+/playlists)");

    private static boolean looksLikeList(String url) {
        return url != null && LIST_HINT.matcher(url).find();
    }

    /** Lee la lista sin bajar nada; si no resulta ser una, sigue el camino normal. */
    private void probeList(final String url) {
        reading = true;
        final Context app = requireContext().getApplicationContext();
        binding.btnAdd.setEnabled(false);
        setStatus(app.getString(R.string.lista_leyendo), true);
        THREADS.execute(() -> {
            Playlist.Result found;
            try {
                Engine.init(app);
                found = Playlist.parse(Engine.playlist(url));
            } catch (Exception e) {
                Logs.append(null, "lista: " + e);
                found = Playlist.parse(null);
            }
            final Playlist.Result list = found;
            main.post(() -> {
                reading = false;
                if (binding == null || !isAdded()) {
                    return;
                }
                binding.btnAdd.setEnabled(true);
                setStatus("", false);
                if (list.ok) {
                    askList(list);
                } else {
                    // "list=" pegado a un video suelto: no era lista, se agrega normal
                    add();
                }
            });
        });
    }

    private void askList(Playlist.Result list) {
        Context context = requireContext();
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.lista_titulo)
                .setMessage(getString(list.trimmed()
                        ? R.string.lista_mensaje_corta : R.string.lista_mensaje,
                        list.title, list.total, list.items.size()))
                .setPositiveButton(getString(R.string.lista_todos, list.items.size()),
                        (dialog, which) -> enqueueList(list))
                .setNegativeButton(R.string.lista_unico, (dialog, which) -> add())
                .setNeutralButton(R.string.clipboard_descartar, null)
                .show();
    }

    /** La lista entra con la calidad guardada, como la pegada de varios links. */
    private void enqueueList(Playlist.Result list) {
        Context context = requireContext();
        String[] fmt = savedQuality(context);
        for (Playlist.Item item : list.items) {
            DownloadService.enqueue(context, item.url, fmt[0], fmt[1]);
        }
        int added = list.items.size();
        binding.urlInput.setText("");
        Toast.makeText(context, getString(R.string.lista_agregadas, added),
                Toast.LENGTH_SHORT).show();
        scrollToNew();
    }

    private void choose(String url, Formats.Option option) {
        Context context = requireContext();
        if (option.needsMerge()) {
            Toast.makeText(context, R.string.fmt_requiere_pc_detalle, Toast.LENGTH_LONG).show();
            return;
        }
        if (option.size > 0) {
            // el tamano lo dio el extractor: se puede avisar antes de gastar la bajada
            confirmSpace(option.size, () -> enqueue(url, option.id,
                    option.shortLabel(context)));
            return;
        }
        enqueue(url, option.id, option.shortLabel(context));
    }

    /**
     * Si lo que se va a bajar no entra (o no deja margen), se muestra cuanto falta y se deja
     * decidir: a veces el que baja sabe que va a borrar algo antes. Con espacio desconocido
     * o sin estimado no se molesta.
     */
    private void confirmSpace(final long needed, final Runnable go) {
        Context context = requireContext();
        long free = Storage.freeBytes(context);
        if (free == 0 || needed + MARGIN <= free) {
            go.run();   // sin dato de espacio, o entra con margen: no se molesta
            return;
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.espacio_titulo)
                .setMessage(getString(R.string.espacio_mensaje, size(needed), size(free)))
                .setPositiveButton(R.string.espacio_igual, (dialog, which) -> go.run())
                .setNegativeButton(R.string.clipboard_descartar, null)
                .show();
    }

    private String size(long bytes) {
        return android.text.format.Formatter.formatShortFileSize(requireContext(), bytes);
    }

    private void enqueue(String url, String fmt, String label) {
        Context context = requireContext();
        // se recuerda la eleccion como "calidad de Ajustes", pero solo si es una preset:
        // un id de formato suelto de este video no tiene por que cambiar la proxima descarga
        if (Quality.isAudio(fmt)) {
            Quality.saveAudio(context, fmt, label);
        } else if (Quality.heightOf(fmt) > 0 || Quality.BEST.equals(fmt)) {
            Quality.save(context, fmt, label);
        }
        DownloadService.enqueue(context, url, fmt, label);
        binding.urlInput.setText("");
        Toast.makeText(context, R.string.agregada, Toast.LENGTH_SHORT).show();
        scrollToNew();
    }

    /**
     * La cola se agrega al final, asi que despues de meter una tarea hay que llevar la
     * lista hasta ahi: si no, el usuario toca el boton y no ve nada nuevo en pantalla.
     */
    private void scrollToNew() {
        androidx.recyclerview.widget.RecyclerView list = binding.queueList;
        list.post(() -> {
            if (binding == null) {
                return;
            }
            int last = adapter.getItemCount() - 1;
            if (last >= 0) {
                list.smoothScrollToPosition(last);
            }
        });
    }

    /**
     * La ficha del video antes de confirmar: titulo, canal, duracion, vistas, alta y cuanto
     * ocupa maso. Todo eso lo devuelve el mismo pedido de formatos, asi que no cuesta ni un
     * request extra y el usuario sabe que esta bajando lo que cree que esta bajando.
     */
    private static String header(Context context, Formats.Result result) {
        StringBuilder title = new StringBuilder();
        if (!result.title.isEmpty()) {
            title.append(result.title);
        }
        StringBuilder facts = new StringBuilder();
        if (!result.uploader.isEmpty()) {
            facts.append(result.uploader);
        }
        if (result.seconds > 0) {
            piece(facts, context.getString(R.string.ficha_duracion, clock(result.seconds)));
        }
        if (result.views > 0) {
            piece(facts, context.getString(R.string.buscar_vistas,
                    SearchAdapter.compact(result.views)));
        }
        if (result.date.length() == 8) {
            piece(facts, result.date.substring(6, 8) + "/" + result.date.substring(4, 6)
                    + "/" + result.date.substring(0, 4));
        }
        if (result.size > 0) {
            piece(facts, android.text.format.Formatter.formatShortFileSize(context,
                    result.size));
        }
        if (facts.length() == 0) {
            return title.toString();
        }
        return title.length() == 0 ? facts.toString() : title + "\n" + facts;
    }

    private static void piece(StringBuilder out, String text) {
        if (out.length() > 0) {
            out.append(" · ");
        }
        out.append(text);
    }

    private static String clock(int total) {
        int minutes = total / 60;
        int seconds = total % 60;
        if (minutes >= 60) {
            return String.format(java.util.Locale.US, "%d:%02d:%02d", minutes / 60,
                    minutes % 60, seconds);
        }
        return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds);
    }

    private void setStatus(String text, boolean visible) {
        if (binding == null) {
            return;
        }
        binding.statusRow.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.statusText.setText(text);
    }

    /** Si hay un link en el portapapeles lo ofrece, pero no descarga nada solo. */
    private void offerClipboard() {
        ClipboardManager clipboard =
                (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()
                || clipboard.getPrimaryClip().getItemCount() == 0) {
            return;
        }
        CharSequence raw = clipboard.getPrimaryClip().getItemAt(0).coerceToText(requireContext());
        final String url = firstUrl(raw == null ? "" : raw.toString());
        if (url == null || url.equals(asked)) {
            return;
        }
        asked = url;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clipboard_titulo)
                .setMessage(getString(R.string.clipboard_mensaje) + "\n\n" + url)
                .setPositiveButton(R.string.clipboard_pegar, (dialog, which) ->
                        binding.urlInput.setText(url))
                .setNegativeButton(R.string.clipboard_descartar, null)
                .show();
    }

    private void clearParts() {
        int deleted = DownloadService.cleanupOrphans(requireContext());
        Toast.makeText(requireContext(),
                getString(R.string.part_borrados, deleted), Toast.LENGTH_SHORT).show();
        refresh();
    }

    private void copyLog() {
        ClipboardManager clipboard =
                (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("ytdl-log", Logs.global()));
        Toast.makeText(requireContext(), R.string.log_copiado, Toast.LENGTH_SHORT).show();
    }

    /** Abre o cierra el filtro de la cola; al cerrar vuelve a mostrar todo. */
    private void toggleSearch() {
        if (binding.filterLayout.getVisibility() == View.VISIBLE) {
            binding.filterInput.setText("");
            adapter.setQuery("");
            binding.filterLayout.setVisibility(View.GONE);
            hideKeyboard();
        } else {
            binding.filterLayout.setVisibility(View.VISIBLE);
            binding.filterInput.requestFocus();
            showKeyboard();
        }
        refresh();
    }

    private void showKeyboard() {
        android.view.inputmethod.InputMethodManager ime =
                (android.view.inputmethod.InputMethodManager) requireContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (ime != null) {
            ime.showSoftInput(binding.filterInput,
                    android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager ime =
                (android.view.inputmethod.InputMethodManager) requireContext()
                        .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (ime != null) {
            ime.hideSoftInputFromWindow(binding.filterInput.getWindowToken(), 0);
        }
    }

    private void refresh() {
        List<Task> tasks = DownloadService.snapshot();
        adapter.submit(tasks);
        boolean empty = adapter.getItemCount() == 0;
        binding.queueEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            binding.queueEmptyText.setText(adapter.hasQuery()
                    ? R.string.cola_sin_resultados : R.string.cola_vacia);
        }
        binding.screenSubtitle.setText(summary(tasks));
        binding.logText.setText(Logs.global());
        // se guarda la vista, no el binding: el post corre despues y el binding ya puede
        // estar borrado si la pestaña cambio mientras tanto
        android.widget.ScrollView scroll = binding.logScroll;
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    /**
     * La bajada de la pantalla deja de ser texto fijo cuando hay cola: muestra el recuento
     * por estado, que es lo que uno quiere saber mirando la pantalla de Descargas.
     */
    private CharSequence summary(List<Task> tasks) {
        int running = 0;
        int queued = 0;
        int paused = 0;
        int error = 0;
        int done = 0;
        for (Task task : tasks) {
            if (Task.RUNNING.equals(task.state)) {
                running++;
            } else if (Task.QUEUED.equals(task.state)) {
                queued++;
            } else if (Task.PAUSED.equals(task.state)) {
                paused++;
            } else if (Task.DONE.equals(task.state)) {
                done++;
            } else if (Task.ERROR.equals(task.state)) {
                error++;
            }
        }
        if (tasks.isEmpty()) {
            return getString(R.string.portada_descargas_sub);
        }
        StringBuilder out = new StringBuilder();
        piece(out, running > 0, getString(R.string.resumen_descargando, running));
        piece(out, queued > 0, getString(R.string.resumen_espera, queued));
        piece(out, paused > 0, getString(R.string.resumen_pausadas, paused));
        piece(out, error > 0, getString(R.string.resumen_error, error));
        piece(out, done > 0, getString(R.string.resumen_listas, done));
        return out;
    }

    private void piece(StringBuilder out, boolean show, String text) {
        if (!show) {
            return;
        }
        if (out.length() > 0) {
            out.append(" · ");
        }
        out.append(text);
    }

    private static final java.util.regex.Pattern HTTP =
            java.util.regex.Pattern.compile("https?://[^\\s\"'<>]+");
    private static final java.util.regex.Pattern BARE =
            java.util.regex.Pattern.compile("(www\\.[^\\s/]+\\.[a-z]{2,}[^\\s]*"
                    + "|[\\w-]+\\.[a-z]{2,}(/[^\\s]*)?)");

    /** Sacar el primer link de un texto, o null si no hay ninguno. */
    static String firstUrl(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        java.util.regex.Matcher matcher = HTTP.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        String trimmed = text.trim();
        return BARE.matcher(trimmed).matches() ? "https://" + trimmed : null;
    }
}
