package com.example.emergency_app.ui.emergency_contact

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.emergency_app.R
import com.example.emergency_app.model.EmergencyContact
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class EmergencyContactAdapter(
    private val items: MutableList<EmergencyContact>,
    private val onCallClick: (String) -> Unit,
    private val onDeleteClick: (EmergencyContact) -> Unit,
    private val onSaveClick: (EmergencyContact) -> Unit // New callback for the "Saved" message
) : RecyclerView.Adapter<EmergencyContactAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_emergency_contact, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], position, onCallClick, onDeleteClick, onSaveClick)
    }

    override fun getItemCount(): Int = items.size

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val btnCall: ImageButton = itemView.findViewById(R.id.btnCall)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEdit) // New Button

        // Inputs
        private val etName: TextInputEditText = itemView.findViewById(R.id.etContactName)
        private val etRelation: TextInputEditText = itemView.findViewById(R.id.etRelationship)
        private val etPhone: TextInputEditText = itemView.findViewById(R.id.etPhone)
        private val etPriority: TextInputEditText = itemView.findViewById(R.id.etPriority)
        private val etAddress: TextInputEditText = itemView.findViewById(R.id.etAddress)

        // Layouts (The wrappers that control animation)
        private val tilName: TextInputLayout = itemView.findViewById(R.id.tilName)
        private val tilRelation: TextInputLayout = itemView.findViewById(R.id.tilRelation)
        private val tilPhone: TextInputLayout = itemView.findViewById(R.id.tilPhone)
        private val tilPriority: TextInputLayout = itemView.findViewById(R.id.tilPriority)
        private val tilAddress: TextInputLayout = itemView.findViewById(R.id.tilAddress)

        // List of all inputs to make enabling/disabling easier
        private val allInputs = listOf(etName, etRelation, etPhone, etPriority, etAddress)

        private var nameWatcher: TextWatcher? = null
        private var relationWatcher: TextWatcher? = null
        private var phoneWatcher: TextWatcher? = null
        private var priorityWatcher: TextWatcher? = null
        private var addressWatcher: TextWatcher? = null

        fun bind(
            contact: EmergencyContact,
            position: Int,
            onCallClick: (String) -> Unit,
            onDeleteClick: (EmergencyContact) -> Unit,
            onSaveClick: (EmergencyContact) -> Unit
        ) {
            tvTitle.text = itemView.context.getString(R.string.title_contact_number, position + 1)

            removeTextWatchers()

            setTextNoAnim(tilName, etName, contact.name)
            setTextNoAnim(tilRelation, etRelation, contact.relationship)
            setTextNoAnim(tilPhone, etPhone, contact.phoneNumber)
            setTextNoAnim(tilPriority, etPriority, if (contact.priority > 0) contact.priority.toString() else "")
            setTextNoAnim(tilAddress, etAddress, contact.address)

            // 2. Handle Edit Mode vs View Mode
            if (contact.isEditing) {
                enableInputs(true)
                btnEdit.setImageResource(R.drawable.ic_check) // "Nike" checkmark
                if (!etName.hasFocus())
                    etName.requestFocus()
            } else {
                enableInputs(false)
                btnEdit.setImageResource(R.drawable.ic_edit)
            }

            btnEdit.setOnClickListener {
                if (contact.isEditing) {
                    // It WAS editing, user clicked SAVE
                    contact.isEditing = false
                    notifyItemChanged(position)
                    onSaveClick(contact)
                } else {
                    // It WAS locked, user clicked EDIT
                    contact.isEditing = true
                    notifyItemChanged(position)
                }
            }

            setupTextWatchers(contact)

            btnCall.setOnClickListener { onCallClick(contact.phoneNumber) }
            btnDelete.setOnClickListener { onDeleteClick(contact) }
        }

        private fun setTextNoAnim(layout: TextInputLayout, editText: TextInputEditText, text: String) {
            val wasEnabled = layout.isHintAnimationEnabled
            layout.isHintAnimationEnabled = false // Turn off animation
            editText.setText(text)                // Set text (snaps instantly)
            layout.isHintAnimationEnabled = wasEnabled // Turn back on for user interaction
        }


        private fun enableInputs(enable: Boolean) {
            allInputs.forEach {
                it.isFocusable = enable
                it.isFocusableInTouchMode = enable
                it.isClickable = enable
                it.isCursorVisible = enable
            }
        }

        private fun setupTextWatchers(contact: EmergencyContact) {
            nameWatcher = object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { contact.name = s.toString() }
            }
            etName.addTextChangedListener(nameWatcher)

            relationWatcher = object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { contact.relationship = s.toString() }
            }
            etRelation.addTextChangedListener(relationWatcher)

            phoneWatcher = object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { contact.phoneNumber = s.toString() }
            }
            etPhone.addTextChangedListener(phoneWatcher)

            priorityWatcher = object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { contact.priority = s.toString().toIntOrNull() ?: 9 }
            }
            etPriority.addTextChangedListener(priorityWatcher)

            addressWatcher = object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { contact.address = s.toString() }
            }
            etAddress.addTextChangedListener(addressWatcher)
        }

        private fun removeTextWatchers() {
            nameWatcher?.let { etName.removeTextChangedListener(it) }
            relationWatcher?.let { etRelation.removeTextChangedListener(it) }
            phoneWatcher?.let { etPhone.removeTextChangedListener(it) }
            priorityWatcher?.let { etPriority.removeTextChangedListener(it) }
            addressWatcher?.let { etAddress.removeTextChangedListener(it) }
        }
    }

    open class SimpleTextWatcher : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {}
    }
}