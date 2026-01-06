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

class EmergencyContactAdapter(
    private val items: MutableList<EmergencyContact>,
    private val onCallClick: (String) -> Unit,
    private val onDeleteClick: (EmergencyContact) -> Unit,
    private val onSaveClick: () -> Unit // New callback for the "Saved" message
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

        // List of all inputs to make enabling/disabling easier
        private val allInputs = listOf(etName, etRelation, etPhone, etPriority, etAddress)

        fun bind(
            contact: EmergencyContact,
            position: Int,
            onCallClick: (String) -> Unit,
            onDeleteClick: (EmergencyContact) -> Unit,
            onSaveClick: () -> Unit
        ) {
            tvTitle.text = itemView.context.getString(R.string.title_contact_number, position + 1)

            // 1. Populate Data
            // We use simple setText because the TextWatchers update the model
            etName.setText(contact.name)
            etRelation.setText(contact.relationship)
            etPhone.setText(contact.phoneNumber)
            etPriority.setText(if (contact.priority > 0) contact.priority.toString() else "")
            etAddress.setText(contact.address)

            // 2. Handle Edit Mode vs View Mode
            if (contact.isEditing) {
                // EDIT MODE: Inputs enabled, Icon is "Checkmark/Save"
                enableInputs(true)
                btnEdit.setImageResource(R.drawable.ic_check) // "Nike" checkmark
                etName.requestFocus() // Focus on the first field
            } else {
                // VIEW MODE: Inputs disabled, Icon is "Pen/Edit"
                enableInputs(false)
                btnEdit.setImageResource(R.drawable.ic_edit)
            }

            // 3. EDIT BUTTON CLICK LISTENER
            btnEdit.setOnClickListener {
                if (contact.isEditing) {
                    // It WAS editing, user clicked SAVE
                    contact.isEditing = false
                    notifyItemChanged(position) // Refresh row to lock fields
                    onSaveClick() // Show "Contact Saved" toast
                } else {
                    // It WAS locked, user clicked EDIT
                    contact.isEditing = true
                    notifyItemChanged(position) // Refresh row to unlock fields
                }
            }

            // 4. Text Watchers (Save as they type)
            setupTextWatchers(contact)

            // 5. Other Buttons
            btnCall.setOnClickListener { onCallClick(contact.phoneNumber) }
            btnDelete.setOnClickListener { onDeleteClick(contact) }
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
            // Basic watchers to keep the object updated
            etName.addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { contact.name = s.toString() }
            })
            etRelation.addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { contact.relationship = s.toString() }
            })
            etPhone.addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { contact.phoneNumber = s.toString() }
            })
            etPriority.addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { contact.priority = s.toString().toIntOrNull() ?: 0 }
            })
            etAddress.addTextChangedListener(object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { contact.address = s.toString() }
            })
        }
    }

    open class SimpleTextWatcher : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {}
    }
}