package com.talha.signatureonpdf;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.util.FitPolicy;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.PdfWriter;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.PDFRenderer;
import com.tom_roush.pdfbox.util.PDFBoxResourceLoader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

public class MainActivity     extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener{
    Bitmap pageImage, drawable;
    RelativeLayout relativeLayout;
    float finalX, finalY;
    float finalXForDB, finalYForDB;
    String TAG = "LOG_MESSAGE", pdfName, pdfPathFromIntent, imageIs;
    ImageView imageView;
    NavigationView navigationView;
    View mHeaderView;
    int x = 0, pageNumber = 1, totalNumberOfPages;
    DrawerLayout drawer;
    Button nxtButton, previewButton, saveBttn;
    private int STORAGE_PERMISSION_CODE = 23;
    File directory, generatedImagesPath;
    ContextWrapper contextWrapper;
    private final int REQUEST_CODE = 100;
    public MainActivity() {}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        relativeLayout = findViewById(R.id.relativelayout);
        PDFBoxResourceLoader.init(this);
        imageView = findViewById(R.id.imageSign);
        nxtButton = findViewById(R.id.nextButton);
        previewButton = findViewById(R.id.previewButton);
        contextWrapper = new ContextWrapper(this);
        directory = contextWrapper.getDir("PdfData", Context.MODE_PRIVATE);
        if (!isReadStorageAllowed()) {
            requestStoragePermission();
        }
        navigationView = findViewById(R.id.nav_view_pdf);
        mHeaderView = navigationView.getHeaderView(0);
        navigationView.setNavigationItemSelectedListener(this);
        drawer = findViewById(R.id.drawer_layout_pdf);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, R.string.navigation_drawer_open, R.string.navigation_drawer_close) {
        };
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        drawer.openDrawer(GravityCompat.START);
        //TODO: change it
        pdfToImage();
        // getDocument();
        //  imageTouchView();
        saveBttn = findViewById(R.id.buttonSave);
        saveBttn.setOnClickListener(v ->

                {
                    imageToPdf();
                }
        );
        generatedImagesPath = new File(directory, x + ".png");
        nxtButton.setOnClickListener(this::onClick);
        previewButton.setOnClickListener(this::onClick);
    }
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.nextButton:
                if (0 < pageNumber && pageNumber < totalNumberOfPages) {
                    ++pageNumber;
                }
                break;
            case R.id.previewButton:
                if (1 < pageNumber && pageNumber <= totalNumberOfPages) {
                    --pageNumber;
                }
                break;
        }

        generatedImagesPath = new File(directory, pageNumber + ".png");
        if (generatedImagesPath.exists()) {
            imageView.setImageURI(Uri.fromFile(generatedImagesPath));
        }


    }

    private void getDocument() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_CODE);

    }

    @Override
    public void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        Uri pdfUri;
        if (data != null && result == RESULT_OK && req == REQUEST_CODE) {
            pdfUri = data.getData();
            pdfPathFromIntent = FileUtils.getPath(this, pdfUri);
            pdfName = FileUtils.getFileName(this, pdfUri);
            pdfToImage();
        } else {
            Snackbar.make(imageView, "Wrong choice", Snackbar.LENGTH_LONG);
        }

    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_sign) {
            drawable= BitmapFactory.decodeResource(getResources(), R.drawable.talha);
            imageTouchView();

        } else if (id == R.id.nav_initials) {
            drawable= BitmapFactory.decodeResource(getResources(), R.drawable.initial);
            imageTouchView();

        } else if (id == R.id.nav_date) {
            imageIs = "signDate";
            drawable = addDate(BitmapFactory.decodeResource(getResources(), R.drawable.date));
            imageTouchView();
        }
        DrawerLayout drawer = findViewById(R.id.drawer_layout_pdf);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }


    public String coordinatesLog() {
        @SuppressLint("DefaultLocale") String msg = String.format("Coordinates: (%.2f,%.2f)", finalX, finalY);
        return msg;
    }

    private boolean isReadStorageAllowed() {
        //Getting the permission status
        int result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);

        //If permission is granted returning true
        if (result == PackageManager.PERMISSION_GRANTED)
            return true;

        //If permission is not granted returning false
        return false;
    }

    private void pdfToImage() {
        FileOutputStream fileOut;
        try {
            AssetManager am = getAssets();
            InputStream is = am.open("sample.pdf");
            //   File file = new File(pdfPathFromIntent);
            PDDocument document = PDDocument.load(is);
            PDFRenderer renderer = new PDFRenderer(document);
            for (int i = 0; document.getNumberOfPages() > i; i++) {
                x++;
                pageImage = renderer.renderImage(i, 1, Bitmap.Config.RGB_565);
                generatedImagesPath = new File(directory, x + ".png");
                fileOut = new FileOutputStream(generatedImagesPath);
                pageImage.compress(Bitmap.CompressFormat.PNG, 100, fileOut);
                fileOut.close();
            }
            totalNumberOfPages = x;
            document.close();
            displayGeneratedImage();
        } catch (IOException e) {
            Log.e("PdfBox-Android-Sample", "Exception thrown while rendering file", e);
            Snackbar.make(imageView, "File not supported", Snackbar.LENGTH_SHORT);
        }

    }

    @SuppressLint("ClickableViewAccessibility")
    private void imageTouchView() {
        if (drawable == null) {
            openDrawerForSign();
        } else {
            imageView.setOnTouchListener((v, event) -> {

                if (drawable == null) {
                    openDrawerForSign();
                } else {
                    int imageViewX, imageViewY;
                    finalX = event.getX();
                    finalY = event.getY();
                    imageViewX = imageView.getDrawable().getIntrinsicWidth();
                    imageViewY = imageView.getDrawable().getIntrinsicHeight();
                    float xCoordinate = event.getX();
                    float yCoordinate = event.getY();
                    finalXForDB = xCoordinate / imageViewX * 100;
                    finalYForDB = yCoordinate / imageViewY * 100;
                    addSignOnImage();
                }

                return false;
            });
        }
    }


    private void openDrawerForSign() {
        Toast toast = Toast.makeText(getApplicationContext(),
                "please choose one", Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, 0, 0);
        View view = toast.getView();
        view.getBackground().setColorFilter(getResources().getColor(R.color.blank), PorterDuff.Mode.SRC_IN);
        toast.show();
        drawer.openDrawer(GravityCompat.START);
    }

    private void imageToPdf() {
        File generatedPdfPath = new File(directory, "xx.pdf");
        File imagesPath;
        ArrayList<String> IMAGES = new ArrayList<>();
        for (int a = 1; a <= totalNumberOfPages; a++) {
            try {
                imagesPath = new File(directory, a + ".png");
                IMAGES.add(imagesPath.toString());
                Image img = Image.getInstance(IMAGES.get(0));
                Document document = new Document(img);
                PdfWriter.getInstance(document, new FileOutputStream(generatedPdfPath));
                document.open();
                for (String image : IMAGES) {
                    img = Image.getInstance(image);
                    document.setPageSize(img);
                    document.newPage();
                    img.setAbsolutePosition(0, 0);
                    document.add(img);
                }
                document.close();
            } catch (IOException | DocumentException e) {
                e.printStackTrace();
            }
        }
        Toast.makeText(this, "File Saved", Toast.LENGTH_SHORT).show();
        showPdf(generatedPdfPath);
    }

    private void showPdf(File pdfFile) {
        Log.d(TAG, "showPdf: " +pdfFile);
        PDFView pdfView;
        pdfView = findViewById(R.id.pdfView);
        pdfView.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.GONE);
        pdfView.fromFile(pdfFile).
                swipeHorizontal(true)
                .scrollHandle(null)
                .pageFitPolicy(FitPolicy.BOTH)
                .pageFling(true)
                .fitEachPage(true).
                enableDoubletap(false).
                enableSwipe(true).
                autoSpacing(false).
                load();
        pdfView.setMaxZoom(1);
        pdfView.setMinZoom(1);
    }

    private void storeImage(Bitmap image) {
        try {
            FileOutputStream fos = new FileOutputStream(generatedImagesPath);
            image.compress(Bitmap.CompressFormat.PNG, 90, fos);
            fos.close();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "Error accessing file: " + e.getMessage());
        }
        imageView.setImageBitmap(image);

    }

    private void requestStoragePermission() {

        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            //If the user has denied the permission previously your code will come to this block
            //Here you can explain why you need this permission
            //Explain here why you need this permission
        }

        //And finally ask for the permission
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        //Checking the request code of our request
        if (requestCode == STORAGE_PERMISSION_CODE) {

            //If permission is granted
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                //Displaying a toast
                Toast.makeText(this, "Permission granted ", Toast.LENGTH_SHORT).show();
            } else {
                //Displaying another toast if permission is not granted
                Toast.makeText(this, "Oops you just denied the permission", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void displayGeneratedImage() {
        generatedImagesPath = new File(directory, "1" + ".png");
        Uri pathUri = Uri.parse(generatedImagesPath.toString());
        new Thread() {
            public void run() {
                runOnUiThread(() -> {
                    imageView.setImageURI(pathUri);
                });
            }
        }.start();

    }//subtle art of not giving
    @Override
    protected void onDestroy() {
        super.onDestroy();
        String[] entries = directory.list();
        for (String s : entries) {
            File currentFile = new File(directory.getPath(), s);
            currentFile.delete();
        }

        // Log.d(TAG, "onDestroy: " + a);
    }

    private Bitmap addDate(Bitmap src) {
        Bitmap dest = Bitmap.createBitmap(400, 150, Bitmap.Config.ARGB_8888);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Calendar calender = Calendar.getInstance();
        String dateTime = sdf.format(calender.getTime()); // reading local time in the system
        Canvas cs = new Canvas(dest);
        Paint tPaint = new Paint();
        tPaint.setTextSize(40f);
        tPaint.setStyle(Paint.Style.FILL);
        //    cs.drawBitmap(src, 0f, 0f, tPaint);
        float height = tPaint.measureText("yY");
        cs.drawText(dateTime, 20f, height + 15f, tPaint);
        return dest;


    }

    public void addSignOnImage() {
        generatedImagesPath = new File(directory, pageNumber + ".png");
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        Bitmap bitmap = BitmapFactory.decodeFile(generatedImagesPath.getAbsolutePath(), bmOptions);
        bitmap = Bitmap.createScaledBitmap(bitmap, pageImage.getWidth(), pageImage.getHeight(), true);
        Bitmap signImage = Bitmap.createScaledBitmap(drawable, 100, 100, false);
        Bitmap resultBitmap = Bitmap.createBitmap(pageImage.getWidth(), pageImage.getHeight(), pageImage.getConfig());
        Canvas canvas = new Canvas(resultBitmap);
        canvas.drawBitmap(bitmap, new Matrix(), null);
        // TODO: here is the x and y coordinates comes
        canvas.drawBitmap(signImage, finalX, finalY, new Paint());
        storeImage(resultBitmap);
        drawable.recycle();
        drawable = null;
    }

}
