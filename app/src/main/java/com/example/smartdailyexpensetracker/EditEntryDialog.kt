package com.example.smartdailyexpensetracker

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

class EditEntryDialog(
    private val context: Context,
    private val entry: Entry,
    private val viewModel: ExpenseViewModel
) {

    fun show() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_entry, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.editTitle)
        val amountInput = dialogView.findViewById<EditText>(R.id.editAmount)
        val categoryInput = dialogView.findViewById<EditText>(R.id.editCategory)
        val typeGroup = dialogView.findViewById<RadioGroup>(R.id.editTypeGroup)
        val typeExpense = dialogView.findViewById<RadioButton>(R.id.editTypeExpense)
        val typeIncome = dialogView.findViewById<RadioButton>(R.id.editTypeIncome)

        // Pre-fill current values
        titleInput.setText(entry.title)
        amountInput.setText(entry.amount.toString())
        categoryInput.setText(entry.category)
        if (entry.type == "income") {
            typeIncome.isChecked = true
        } else {
            typeExpense.isChecked = true
        }

        AlertDialog.Builder(context)
            .setTitle("Edit Entry")
            .setView(dialogView)
            .setPositiveButton("Update") { dialog, _ ->
                val newTitle = titleInput.text.toString().trim()
                val newAmountText = amountInput.text.toString().trim()
                val newCategory = categoryInput.text.toString().trim()
                val newType = if (typeIncome.isChecked) "income" else "expense"

                if (newTitle.isEmpty() || newAmountText.isEmpty() || newCategory.isEmpty()) {
                    Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val newAmount = try {
                    newAmountText.toDouble()
                } catch (e: NumberFormatException) {
                    Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (newAmount <= 0) {
                    Toast.makeText(context, "Please enter a positive amount", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                viewModel.updateEntry(entry, newTitle, newAmount, newCategory, newType)
                Toast.makeText(context, "Entry updated successfully", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("Delete") { dialog, _ ->
                // Show confirmation before deletion
                showDeleteConfirmation(entry)
                dialog.dismiss()
            }
            .show()
    }

    private fun showDeleteConfirmation(entry: Entry) {
        AlertDialog.Builder(context)
            .setTitle("Delete Entry")
            .setMessage("Are you sure you want to delete '${entry.title}' ($${entry.amount})?")
            .setPositiveButton("Delete") { dialog, _ ->
                viewModel.deleteEntry(entry)
                Toast.makeText(context, "Entry deleted successfully", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }
}