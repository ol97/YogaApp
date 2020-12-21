package com.example.yogaapp

import android.text.format.DateUtils
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.navigation.Navigation
import java.lang.Long.parseLong


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
        holder.textViewTime.text = item[3]
        holder.id = item[0]
        holder.name = item[1]
        holder.date = item[2]
        holder.time = item[3]
        holder.duration = item[4]

    }

    override fun getItemCount(): Int = values.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view), View.OnClickListener {
        val textViewDate: TextView = view.findViewById(R.id.textViewDate)
        val textViewTime: TextView = view.findViewById(R.id.textViewTime)
        val textViewName: TextView = view.findViewById(R.id.textViewName)

        lateinit var id: String
        lateinit var date: String
        lateinit var time: String
        lateinit var name: String
        lateinit var duration: String


        init {
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            val navController = v?.let { Navigation.findNavController(it) }

            navController?.navigate(ItemListFragmentDirections.actionItemListFragmentToSessionDetailsFragment(id, date, time, duration, name))

        }

    }
}