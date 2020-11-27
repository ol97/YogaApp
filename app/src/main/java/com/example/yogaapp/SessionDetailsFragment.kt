package com.example.yogaapp

import android.content.DialogInterface
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.yogaapp.database.ArchiveHelper
import java.lang.Long.parseLong


class SessionDetailsFragment : Fragment() {
    private val args: SessionDetailsFragmentArgs by navArgs()

    private lateinit var textViewName: TextView
    private lateinit var textViewDate: TextView
    private lateinit var textViewHour: TextView
    private lateinit var textViewDuration: TextView
    private lateinit var textViewPoses: TextView
    private lateinit var buttonDelete: Button
    private lateinit var buttonRename: Button



    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_session_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textViewName = view.findViewById(R.id.textViewName)
        textViewDate = view.findViewById(R.id.textViewDate)
        textViewHour = view.findViewById(R.id.textViewHour)
        textViewDuration = view.findViewById(R.id.textViewDuration)
        textViewPoses = view.findViewById(R.id.textViewPoses)
        textViewPoses.movementMethod = ScrollingMovementMethod()
        buttonDelete = view.findViewById(R.id.buttonDelete)
        buttonRename = view.findViewById(R.id.buttonRename)

        textViewName.text = "Name: " + args.name
        textViewDate.text = "Date: " + args.date
        textViewHour.text = "Time: "+ args.hour
        textViewDuration.text = "Duration: " + (parseLong(args.duration)/1000F).toString() + "seconds"

        buttonDelete.setOnClickListener {

            val alert = context?.let { it1 -> AlertDialog.Builder(it1) }
            alert?.setTitle("Warning")
            alert?.setMessage("Confirm deletion")

            alert?.setPositiveButton("DELETE") { dialog, whichButton ->
                context?.let { it1 ->
                    val ok = ArchiveHelper.getInstance(it1)?.deleteSession(args.sessionid)
                    if (ok!!) {
                        findNavController().navigate(SessionDetailsFragmentDirections.actionSessionDetailsFragmentToItemListFragment())
                    }
                }
            }

            alert?.setNegativeButton("CANCEL"
            ) { dialog, which ->
            }
            alert?.show()
        }

        buttonRename.setOnClickListener {
            val alert = context?.let { it1 -> AlertDialog.Builder(it1) }
            alert?.setTitle("Rename")
            alert?.setMessage("Insert new name")

            val input = EditText(context)
            alert?.setView(input)

            alert?.setPositiveButton("Ok") { dialog, whichButton ->
                val value = input.text.toString()
                context?.let { it1 -> ArchiveHelper.getInstance(it1)?.changeSessionName(value, args.sessionid) }
                updateUI()
            }

            alert?.setNegativeButton("Cancel"
            ) { dialog, which ->
            }
            alert?.show()
        }
    }

    override fun onResume() {
        super.onResume()

        val detailedData = context?.let {
            ArchiveHelper.getInstance(it)?.readDetailedSessionData(args.sessionid) }

        for (i in detailedData!!.indices)
        {
            val oldText = textViewPoses.text
            val newText = oldText.toString() + detailedData[i][0] + " " +
                    detailedData[i][1] + "   " + (parseLong(detailedData[i][2])/1000F).toString() +
                    " seconds" + "\n"
            textViewPoses.text = newText
        }

    }

    private fun updateUI(){
        val detailedData = context?.let {
            ArchiveHelper.getInstance(it)?.readDetailedSessionData(args.sessionid) }

        for (i in detailedData!!.indices)
        {
            val oldText = textViewPoses.text
            val newText = oldText.toString() + detailedData[i][0] + " " +
                    detailedData[i][1] + "   " + (parseLong(detailedData[i][2])/1000F).toString() +
                    " seconds" + "\n"
            textViewPoses.text = newText
        }

        val sessionData = context?.let { ArchiveHelper.getInstance(it)?.readSessionData(args.sessionid) }
        if (sessionData!!.isNotEmpty())
        {
            textViewDate.text = "Date: " + sessionData[2]
            textViewName.text = "Name: " + sessionData[1]
            textViewHour.text = "Time: " + sessionData[3]
            textViewDuration.text = "Duration: " + sessionData[4]
        }
    }

}