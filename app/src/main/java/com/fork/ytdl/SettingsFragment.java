package com.fork.ytdl;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import com.fork.ytdl.databinding.FragmentSettingsBinding;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private ActivityResultLauncher<Uri> treePicker;

    @Override
    public void onCreate(@Nullable Bundle saved) {
        super.onCreate(saved);
        treePicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(), this::chooseTree);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle saved) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle saved) {
        String[] info = Engine.info();
        binding.lblEngineName.setText(info[0]);
        binding.lblEngineVersion.setText(
                getString(R.string.ajustes_version) + ": " + info[1]);
        binding.lblEngineUpdated.setText(getString(R.string.ajustes_actualizado) + ": "
                + getString(R.string.ajustes_pendiente));
        binding.btnUpdateEngine.setEnabled(false);

        binding.btnPickDir.setOnClickListener(v -> treePicker.launch(null));
        binding.btnPickDir.setOnLongClickListener(v -> {
            Storage.saveTree(requireContext(), null);
            refreshDir();
            Toast.makeText(requireContext(), R.string.carpeta_interna, Toast.LENGTH_SHORT).show();
            return true;
        });

        refreshDir();
        binding.lblQuality.setText(
                Quality.label(requireContext(), Quality.saved(requireContext())));
    }

    private void chooseTree(@Nullable Uri uri) {
        if (uri == null) {
            return;
        }
        Storage.saveTree(requireContext(), uri);
        refreshDir();
        Toast.makeText(requireContext(),
                getString(R.string.carpeta_elegida, Storage.describe(requireContext())),
                Toast.LENGTH_LONG).show();
    }

    private void refreshDir() {
        binding.lblDir.setText(Storage.describe(requireContext()));
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
