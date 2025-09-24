package com.example.smartdailyexpensetracker

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.fragment.app.DialogFragment

class BudgetDialog(
    private val currentBudget: Double? = null,
    private val onBudgetSet: (Double) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_budget, null)

        val budgetInput = view.findViewById<EditText>(R.id.budgetInput)

        currentBudget?.let {
            budgetInput.setText(it.toString())
        }

        return AlertDialog.Builder(requireActivity())
            .setView(view)
            .setTitle(if (currentBudget != null) "Update Monthly Budget" else "Set Monthly Budget")
            .setPositiveButton("Set Budget") { _, _ ->
                val budgetText = budgetInput.text.toString().trim()
                if (budgetText.isNotEmpty()) {
                    val budget = budgetText.toDoubleOrNull()
                    if (budget != null && budget > 0) {
                        onBudgetSet(budget)
                    }
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
    }
}