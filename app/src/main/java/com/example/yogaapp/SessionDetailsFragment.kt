package com.example.yogaapp

import android.os.Bundle
import android.text.format.DateUtils
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.yogaapp.database.ArchiveHelper
import java.lang.Long.parseLong

// fragment in which details of a training session are displayed
class SessionDetailsFragment : Fragment() {

    // reading data passed from list fragment
    private val args: SessionDetailsFragmentArgs by navArgs()

    // widgets used in this fragment
    private lateinit var textViewName: TextView
    private lateinit var textViewDate: TextView
    private lateinit var textViewTime: TextView
    private lateinit var textViewDuration: TextView
    private lateinit var textViewPoses: TextView
    private lateinit var buttonDelete: Button
    private lateinit var buttonRename: Button

    // inflate layout
    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_session_details, container, false)
    }

    // after the layout is inflated find all views and set texts
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textViewName = view.findViewById(R.id.textViewName)
        textViewDate = view.findViewById(R.id.textViewDate)
        textViewTime = view.findViewById(R.id.textViewTime)
        textViewDuration = view.findViewById(R.id.textViewDuration)
        textViewPoses = view.findViewById(R.id.textViewPoses)
        textViewPoses.movementMethod = ScrollingMovementMethod()
        buttonDelete = view.findViewById(R.id.buttonDelete)
        buttonRename = view.findViewById(R.id.buttonRename)

        textViewName.text = getString(R.string.nameTextView, args.name)
        textViewDate.text = getString(R.string.dateTextView, args.date)
        textViewTime.text = getString(R.string.timeTextView, args.time)
        textViewDuration.text = getString(R.string.durationTextView, DateUtils.formatElapsedTime((parseLong(args.duration)/1000F).toLong()))

        // after "Delete" button is pressed display dialog asking for confirmation
        buttonDelete.setOnClickListener {

            val alert = context?.let { it1 -> AlertDialog.Builder(it1) }
            alert?.setTitle(getString(R.string.warning))
            alert?.setMessage(getString(R.string.deleteConfirmation))

            alert?.setPositiveButton(getString(R.string.delete)) { dialog, whichButton ->
                context?.let { it1 ->
                    val ok = ArchiveHelper.getInstance(it1)?.deleteSession(args.sessionid)
                    if (ok!!) {
                        // if the session is deleted go back to list fragment
                        findNavController().navigate(SessionDetailsFragmentDirections.actionSessionDetailsFragmentToItemListFragment())
                    }
                }
            }

            alert?.setNegativeButton(getString(R.string.cancel)
            ) { dialog, which ->
            }
            alert?.show()
        }

        // after "Rename" button is clicked display dialog in which user is supposed to insert new name
        buttonRename.setOnClickListener {
            val names = context?.let { ArchiveHelper.getInstance(it) }!!.readSessionNames()

            val alert = context?.let { it1 -> AlertDialog.Builder(it1) }
            alert?.setTitle(getString(R.string.rename))
            alert?.setMessage(getString(R.string.insertNewName))
            val input = EditText(context)
            alert?.setView(input)

            alert?.setPositiveButton(getString(R.string.ok)) { dialog, whichButton ->
                val newName = input.text.toString()

                // check if name is already taken, if yes show Toast
                if (names.contains(newName)) {
                    val toast = Toast.makeText(context, getString(R.string.nameInUse), Toast.LENGTH_SHORT)
                    toast.show()
                }
                // check if name is too long, if yes show Toast
                else if (newName.length >= 20){
                    val toast = Toast.makeText(context, getString(R.string.nameTooLong), Toast.LENGTH_SHORT)
                    toast.show()
                }
                // if new name is valid perform renaming operation and update UI
                else {
                    context?.let { it1 -> ArchiveHelper.getInstance(it1)?.changeSessionName(newName, args.sessionid) }
                    updateUI()
                }
            }

            alert?.setNegativeButton(getString(R.string.cancel)
            ) { dialog, which ->
            }
            alert?.show()
        }
    }

    // update UI after loading the fragment
    override fun onResume() {
        super.onResume()

        updateUI()
    }


    private fun updateUI(){
        // read sequence of poses
        val detailedData = context?.let {
            ArchiveHelper.getInstance(it)?.readPoseSequence(args.sessionid) }

        // display sequence in a list in textView
        // duration is converted from milliseconds to seconds
        // iterates through list of poses and adds them one by one to the string
        // which is later displayed
        textViewPoses.text = ""
        for (i in detailedData!!.indices)
        {
            val oldText = textViewPoses.text
            val newText = oldText.toString() + detailedData[i][0] + " " +
                    detailedData[i][1] + "   " + (parseLong(detailedData[i][2])/1000F).toString() +
                    " " + getString(R.string.seconds) + "\n"
            textViewPoses.text = newText
        }

        // read data from database and update UI
        // some data is passed when navigating from list fragment to this one, but the name may change
        // so might as well read everything
        val sessionData = context?.let { ArchiveHelper.getInstance(it)?.readBasicSessionDetails(args.sessionid) }
        if (sessionData!!.isNotEmpty())
        {
            textViewDate.text = getString(R.string.dateTextView, sessionData[2])
            textViewName.text = getString(R.string.nameTextView, sessionData[1])
            textViewTime.text = getString(R.string.timeTextView, sessionData[3])
            textViewDuration.text = getString(R.string.durationTextView,
                    DateUtils.formatElapsedTime(parseLong(sessionData[4])/1000L))
        }
    }

}