package com.example.smartdailyexpensetracker

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputLayout

class EditExpenseDialog(
    private val expense: Expense,
    private val onUpdate: (Expense, String, Double, String) -> Unit,
    private val onDelete: (Expense) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_edit_expense, null)

        val titleInput = view.findViewById<EditText>(R.id.editTitle)
        val amountInput = view.findViewById<EditText>(R.id.editAmount)
        val categoryInput = view.findViewById<EditText>(R.id.editCategory)

        // Pre-fill with existing data
        titleInput.setText(expense.title)
        amountInput.setText(expense.amount.toString())
        categoryInput.setText(expense.category)

        return AlertDialog.Builder(requireActivity())
            .setView(view)
            .setTitle("Edit Expense")
            .setPositiveButton("Update") { _, _ ->
                val newTitle = titleInput.text.toString().trim()
                val newAmountText = amountInput.text.toString().trim()
                val newCategory = categoryInput.text.toString().trim()

                if (newTitle.isEmpty() || newAmountText.isEmpty() || newCategory.isEmpty()) {
                    return@setPositiveButton
                }

                val newAmount = try {
                    newAmountText.toDouble()
                } catch (e: NumberFormatException) {
                    0.0
                }

                onUpdate(expense, newTitle, newAmount, newCategory)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("Delete") { _, _ ->
                onDelete(expense)
            }
            .create()
    }
}