package com.example.yogaapp

import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.navigation.Navigation

import com.example.yogaapp.dummy.DummyContent.DummyItem


class MyItemRecyclerViewAdapter(
    private val values: List<Array<String>>
) : RecyclerView.Adapter<MyItemRecyclerViewAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_item_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = values[position]
        holder.textViewName.text = item[1]
        holder.textViewDate.text = item[2]
        holder.textViewHour.text = item[3]

        val id = item[0]
    }

    override fun getItemCount(): Int = values.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textViewDate: TextView = view.findViewById(R.id.textViewDate)
        val textViewHour: TextView = view.findViewById(R.id.textViewHour)
        val textViewName: TextView = view.findViewById(R.id.textViewName)

    }
}