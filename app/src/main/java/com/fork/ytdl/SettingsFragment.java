package com.fork.ytdl;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.fork.ytdl.databinding.FragmentSettingsBinding;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;

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

        binding.lblDir.setText(DownloadService.outputDir(requireContext()).getAbsolutePath());
        binding.lblQuality.setText(Quality.label(requireContext(), Quality.saved(requireContext())));

        binding.btnUpdateEngine.setEnabled(false);
        binding.btnPickDir.setEnabled(false);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}
