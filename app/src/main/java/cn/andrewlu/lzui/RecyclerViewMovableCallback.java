//package cn.andrewlu.lzui;
//
//import android.support.v7.widget.LinearLayoutManager;
//import android.support.v7.widget.RecyclerView;
//import android.support.v7.widget.StaggeredGridLayoutManager;
//import android.view.View;
//import android.widget.AbsListView;
//
///**
// *如果项目中需要支持RecyCleView的下拉刷新,则取消本类的注释即可.不需要再做任何额外操作.
// * Created by andrewlu on 2015/12/30.
// */
//public class RecyclerViewMovableCallback implements SuperRefreshLayout.MovableCallback {
//    @Override
//    public Boolean canMove(View mTarget, int direction) {
//        if (mTarget instanceof RecyclerView) {
//            RecyclerView recyclerView = (RecyclerView) mTarget;
//            RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
//            int count = recyclerView.getAdapter().getItemCount();
//            if (layoutManager instanceof LinearLayoutManager && count > 0) {
//                LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
//                if (linearLayoutManager.findFirstCompletelyVisibleItemPosition() == 0 && direction > 0) {
//                    //下拉。
//                    return true;
//                } else if (linearLayoutManager.findLastCompletelyVisibleItemPosition() == count - 1 && direction < 0) {
//                    return true;
//                }
//            } else if (layoutManager instanceof StaggeredGridLayoutManager) {
//                StaggeredGridLayoutManager staggeredGridLayoutManager = (StaggeredGridLayoutManager) layoutManager;
//                int[] lastItems = new int[2];
//                staggeredGridLayoutManager
//                        .findLastCompletelyVisibleItemPositions(lastItems);
//                int lastItem = Math.max(lastItems[0], lastItems[1]);
//                if (lastItem == count - 1) {
//                    return true;
//                }
//            }
//            return false;
//        }
//        return null;
//    }
//
//    static {
//        SuperRefreshLayout.registerMovableCallback(new RecyclerViewMovableCallback());
//    }
//}
