// SPDX-License-Identifier: MIT
package io.github.kuscher.canvas;

import android.app.Activity;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintManager;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * File > Print: Patchy renders the page into a PDF (write_print_pdf), and Android's
 * print dialog takes it from there: printer, paper, copies, or Save as PDF.
 */
public final class CanvasPrint {
    private static final String TAG = "Canvas";

    private CanvasPrint() {}

    /** Called from C++ with a finished one-page PDF. */
    public static void printPdf(final String path, final String jobName) {
        final Activity activity = CanvasActivity.currentActivity();
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(() -> {
            PrintManager printing = activity.getSystemService(PrintManager.class);
            if (printing != null) {
                printing.print(jobName, new PdfAdapter(path, jobName), new PrintAttributes.Builder().build());
            }
        });
    }

    private static final class PdfAdapter extends PrintDocumentAdapter {
        private final String path;
        private final String name;

        PdfAdapter(String path, String name) {
            this.path = path;
            this.name = name.endsWith(".pdf") ? name : name + ".pdf";
        }

        @Override
        public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
                CancellationSignal cancellationSignal, LayoutResultCallback callback, Bundle extras) {
            if (cancellationSignal.isCanceled()) {
                callback.onLayoutCancelled();
                return;
            }
            PrintDocumentInfo info = new PrintDocumentInfo.Builder(name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO)
                    .setPageCount(1)
                    .build();
            callback.onLayoutFinished(info, !newAttributes.equals(oldAttributes));
        }

        @Override
        public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                CancellationSignal cancellationSignal, WriteResultCallback callback) {
            try (InputStream in = new FileInputStream(path);
                 OutputStream out = new FileOutputStream(destination.getFileDescriptor())) {
                byte[] buffer = new byte[1 << 16];
                for (int n; (n = in.read(buffer)) > 0; ) {
                    if (cancellationSignal.isCanceled()) {
                        callback.onWriteCancelled();
                        return;
                    }
                    out.write(buffer, 0, n);
                }
                callback.onWriteFinished(new PageRange[] {PageRange.ALL_PAGES});
            } catch (Exception e) {
                Log.w(TAG, "print " + path + ": " + e);
                callback.onWriteFailed(e.toString());
            }
        }
    }
}
