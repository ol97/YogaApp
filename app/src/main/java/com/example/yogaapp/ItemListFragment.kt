package com.example.yogaapp

import android.content.res.Resources
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DividerItemDecoration
import com.example.yogaapp.database.ArchiveHelper


class ItemListFragment : Fragment() {

    private var columnCount = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            columnCount = it.getInt(ARG_COLUMN_COUNT)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_item_list_list, container, false)

        if (view is RecyclerView) {
            with(view) {
                layoutManager = when {
                    columnCount <= 1 -> LinearLayoutManager(context)
                    else -> GridLayoutManager(context, columnCount)
                }
                adapter = MyItemRecyclerViewAdapter(ArchiveHelper.getInstance(context)?.readSessions()!!)
            }
            val divider = DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
            ResourcesCompat.getDrawable(resources, R.drawable.recyclerview_divider, null)?.let { divider.setDrawable(it) }
            view.addItemDecoration(divider)
        }
        return view
    }

    companion object {
        const val ARG_COLUMN_COUNT = "column-count"
    }
}