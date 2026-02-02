package com.example.emergency_app.ui.emergency_contact

import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.emergency_app.R
import com.example.emergency_app.model.EmergencyContact
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class EmergencyContactAdapter(
    private val items: MutableList<EmergencyContact>,
    private val onCallClick: (String) -> Unit,
    private val onDeleteClick: (EmergencyContact) -> Unit,
    private val onSaveClick: (EmergencyContact) -> Unit // New callback for the "Saved" message
) : RecyclerView.Adapter<EmergencyContactAdapter.VH>() {

    private val editSnapshots = mutableMapOf<String, EmergencyContact>()

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
        private val viewContainer: View = itemView.findViewById(R.id.viewContainer)
        private val editContainer: View = itemView.findViewById(R.id.editContainer)
        private val tvAvatar: TextView = itemView.findViewById(R.id.tvAvatar)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvPhone: TextView = itemView.findViewById(R.id.tvPhone)
        private val tvPriorityBadge: TextView = itemView.findViewById(R.id.tvPriorityBadge)
        private val btnCall: ImageButton = itemView.findViewById(R.id.btnCall)
        private val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDelete)
        private val btnEdit: MaterialButton = itemView.findViewById(R.id.btnEdit)

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
            removeTextWatchers()

            setTextNoAnim(tilName, etName, contact.name)
            setTextNoAnim(tilRelation, etRelation, contact.relationship)
            setTextNoAnim(tilPhone, etPhone, contact.phoneNumber)
            setTextNoAnim(tilPriority, etPriority, if (contact.priority > 0) contact.priority.toString() else "")
            setTextNoAnim(tilAddress, etAddress, contact.address)

            tvName.text = contact.name.ifBlank {
                itemView.context.getString(R.string.not_set)
            }
            tvPhone.text = contact.phoneNumber.ifBlank {
                itemView.context.getString(R.string.not_set)
            }
            tvPriorityBadge.text = if (contact.priority > 0) contact.priority.toString() else "-"
            tvAvatar.text = contact.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

            if (contact.isEditing) {
                viewContainer.visibility = View.GONE
                editContainer.visibility = View.VISIBLE
                enableInputs(true)
                btnEdit.text = itemView.context.getString(R.string.save)
                btnEdit.setIconResource(R.drawable.ic_check)
                btnEdit.setTextColor(ContextCompat.getColor(itemView.context, R.color.white))
                btnEdit.setIconTintResource(R.color.white)
                btnEdit.backgroundTintList = ContextCompat.getColorStateList(itemView.context, R.color.header_red)

                btnDelete.text = itemView.context.getString(R.string.cancel_changes)
                btnDelete.setIconResource(android.R.drawable.ic_menu_close_clear_cancel)
                btnDelete.setTextColor(ContextCompat.getColor(itemView.context, R.color.medical_text_secondary))
                btnDelete.setIconTintResource(R.color.medical_text_secondary)
                btnDelete.backgroundTintList = ContextCompat.getColorStateList(itemView.context, R.color.medical_card_background)
                btnDelete.strokeColor = ContextCompat.getColorStateList(itemView.context, R.color.medical_input_stroke)

                updateSaveEnabled(true)

                if (!etName.hasFocus()) {
                    etName.requestFocus()
                }
            } else {
                viewContainer.visibility = View.VISIBLE
                editContainer.visibility = View.GONE
                enableInputs(false)
                btnEdit.text = itemView.context.getString(R.string.edit)
                btnEdit.setIconResource(R.drawable.ic_edit)
                btnEdit.setTextColor(ContextCompat.getColor(itemView.context, R.color.medical_text_primary))
                btnEdit.setIconTintResource(R.color.medical_text_primary)
                btnEdit.backgroundTintList = ContextCompat.getColorStateList(itemView.context, R.color.medical_card_background)
                btnEdit.strokeColor = ContextCompat.getColorStateList(itemView.context, R.color.medical_input_stroke)
                btnEdit.isEnabled = true
                btnEdit.alpha = 1f

                btnDelete.text = itemView.context.getString(R.string.delete)
                btnDelete.setIconResource(R.drawable.ic_delete)
                btnDelete.setTextColor(ContextCompat.getColor(itemView.context, R.color.header_red))
                btnDelete.setIconTintResource(R.color.header_red)
                btnDelete.backgroundTintList = ContextCompat.getColorStateList(itemView.context, R.color.medical_card_background)
                btnDelete.strokeColor = ContextCompat.getColorStateList(itemView.context, R.color.header_red)

                editSnapshots.remove(contact.id)
            }

            btnEdit.setOnClickListener {
                if (contact.isEditing) {
                    if (!btnEdit.isEnabled) {
                        Toast.makeText(itemView.context, R.string.required_fields_message, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    contact.isEditing = false
                    editSnapshots.remove(contact.id)
                    notifyItemChanged(position)
                    onSaveClick(contact)
                } else {
                    contact.isEditing = true
                    editSnapshots[contact.id] = contact.copy()
                    notifyItemChanged(position)
                }
            }

            btnDelete.setOnClickListener {
                if (contact.isEditing) {
                    editSnapshots[contact.id]?.let { snapshot ->
                        contact.name = snapshot.name
                        contact.relationship = snapshot.relationship
                        contact.phoneNumber = snapshot.phoneNumber
                        contact.priority = snapshot.priority
                        contact.address = snapshot.address
                    }
                    contact.isEditing = false
                    editSnapshots.remove(contact.id)
                    notifyItemChanged(position)
                } else {
                    onDeleteClick(contact)
                }
            }

            setupTextWatchers(contact)

            btnCall.setOnClickListener { onCallClick(contact.phoneNumber) }
        }

        private fun updateSaveEnabled(isEditing: Boolean) {
            if (!isEditing) {
                btnEdit.isEnabled = true
                btnEdit.alpha = 1f
                return
            }
            val hasName = etName.text?.toString()?.trim().orEmpty().isNotEmpty()
            val hasPhone = etPhone.text?.toString()?.trim().orEmpty().isNotEmpty()
            val enabled = hasName && hasPhone
            btnEdit.isEnabled = enabled
            btnEdit.alpha = if (enabled) 1f else 0.5f
        }

        private fun setTextNoAnim(layout: TextInputLayout, editText: TextInputEditText, text: String) {
            val wasEnabled = layout.isHintAnimationEnabled
            layout.isHintAnimationEnabled = false // Turn off animation
            editText.setText(text)
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
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    contact.name = s.toString()
                    if (contact.isEditing) updateSaveEnabled(true)
                }
            }
            etName.addTextChangedListener(nameWatcher)

            relationWatcher = object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { contact.relationship = s.toString() }
            }
            etRelation.addTextChangedListener(relationWatcher)

            phoneWatcher = object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    contact.phoneNumber = s.toString()
                    if (contact.isEditing) updateSaveEnabled(true)
                }
            }
            etPhone.addTextChangedListener(phoneWatcher)

            priorityWatcher = object : SimpleTextWatcher() {
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { contact.priority = s.toString().toIntOrNull() ?: 0 }
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