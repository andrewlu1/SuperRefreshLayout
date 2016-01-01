package cn.andrewlu.lzui;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SuperRefreshLayout refreshLayout = (SuperRefreshLayout) findViewById(R.id.refreshLayout);
        //refreshLayout.setDownEnable(false);
        //refreshLayout.setUpEnable(false);
        //refreshLayout.startUpRefreshing();
        //refreshLayout.startDownRefreshing();
        refreshLayout.setOnRefreshListener(new SuperRefreshLayout.OnRefreshListener() {
            @Override
            public void OnRefresh(final SuperRefreshLayout refresher, boolean up) {
                new Thread() {
                    public void run() {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        refresher.finishRefresh();
                    }
                }.start();
            }
        });

        findViewById(R.id.img).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "你点击了元素", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
