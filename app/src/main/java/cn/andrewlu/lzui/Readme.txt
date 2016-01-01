SuperRefreshLayout可支持绝大多数布局的下拉刷新及上拉加载,甚至是一张图片,一个文本框.
只需要像ScrollView一样套在需要刷新的控件的外层即可.
有一点要注意:与ScrollView一样,本控件设计只能用且仅有一个直接子控件.
本控件默认不引入RecyclerView的支持(因为很多人还不知道RecyclerView是何物).如果需要支持RecyclerView,只需要取消类RecyclerViewMovableCallback中的注释即可.
不需要再做任何别的操作.

典型的方式如下示例:

    <cn.andrewlu.lzui.SuperRefreshLayout
        android:id="@+id/refreshLayout"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <ScrollView
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <TextView
                android:id="@+id/text"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="时间:" />
        </ScrollView>
    </cn.andrewlu.lzui.SuperRefreshLayout>


    <cn.andrewlu.lzui.SuperRefreshLayout
        android:id="@+id/refreshLayout"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <ListView
            android:layout_width="match_parent"
            android:layout_height="match_parent">
        </ListView>
    </cn.andrewlu.lzui.SuperRefreshLayout>
</RelativeLayout>

    <cn.andrewlu.lzui.SuperRefreshLayout
        android:id="@+id/refreshLayout"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <ImageView
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:src="...">
    </cn.andrewlu.lzui.SuperRefreshLayout>
</RelativeLayout>