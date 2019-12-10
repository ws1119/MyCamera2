package com.example.photograph;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;

public class ShowImageActivity extends AppCompatActivity {

    private ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_image);
        imageView=findViewById(R.id.image);
        Intent intent = getIntent();
        String path = intent.getStringExtra("image");
        imageView.setImageURI(Uri.parse(path));
    }
}
