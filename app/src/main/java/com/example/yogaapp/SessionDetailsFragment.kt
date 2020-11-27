package com.example.yogaapp

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.navigation.fragment.navArgs
import com.example.yogaapp.database.ArchiveHelper
import java.lang.Long.parseLong

class SessionDetailsFragment : Fragment() {
    val args: SessionDetailsFragmentArgs by navArgs()

    private lateinit var textViewName: TextView
    private lateinit var textViewDate: TextView
    private lateinit var textViewHour: TextView
    private lateinit var textViewDuration: TextView
    private lateinit var textViewPoses: TextView
    private lateinit var buttonDelete: Button



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

        textViewName.text = "Name: " + args.name
        textViewDate.text = "Date: " + args.date
        textViewHour.text = "Hour: "+ args.hour
        textViewDuration.text = "Duration: " + (parseLong(args.duration)/1000F).toString() + "seconds"

    }

    override fun onResume() {
        super.onResume()

        val sessionData = context?.let {
            ArchiveHelper.getInstance(it)?.readDetailedSessionData(args.sessionid) }

        for (i in sessionData!!.indices)
        {
            var oldText = textViewPoses.text
            var newText = oldText.toString() + sessionData[i][0] + " " +
                    sessionData[i][1] + "   " + (parseLong(sessionData[i][2])/1000F).toString() +
                    " seconds" + "\n"
            textViewPoses.text = newText
        }

    }

}