package com.example.tcg_tracker.util

import android.content.Context
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import com.example.tcg_tracker.data.CardFolder
import com.example.tcg_tracker.data.MockRepository
import com.example.tcg_tracker.data.PokemonCard
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object FolderDialogs {

    /** Marca/desmarca en qué listas está la carta. Permite crear una lista nueva desde el propio diálogo. */
    fun showAddToList(context: Context, card: PokemonCard, onChanged: () -> Unit = {}) {
        val folders = MockRepository.getFolders()

        if (folders.isEmpty()) {
            MaterialAlertDialogBuilder(context)
                .setTitle("Aún no tienes ningún álbum")
                .setMessage("Crea un álbum para guardar \"${card.name}\".")
                .setPositiveButton("Crear álbum") { _, _ ->
                    promptNewList(context) { folder ->
                        MockRepository.addCardToFolder(folder.id, card.id)
                        Toast.makeText(context, "Añadida a \"${folder.title}\"", Toast.LENGTH_SHORT).show()
                        onChanged()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }

        val names = folders.map { it.title }.toTypedArray()
        val initial = BooleanArray(folders.size) { MockRepository.isCardInFolder(folders[it].id, card.id) }
        val checked = initial.copyOf()

        MaterialAlertDialogBuilder(context)
            .setTitle("Añadir \"${card.name}\" a álbum")
            .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Guardar") { _, _ ->
                folders.forEachIndexed { i, folder ->
                    if (checked[i] && !initial[i]) MockRepository.addCardToFolder(folder.id, card.id)
                    if (!checked[i] && initial[i]) MockRepository.removeCardFromFolder(folder.id, card.id)
                }
                Toast.makeText(context, "Álbumes actualizados", Toast.LENGTH_SHORT).show()
                onChanged()
            }
            .setNeutralButton("Nuevo Álbum") { _, _ ->
                promptNewList(context) { folder ->
                    MockRepository.addCardToFolder(folder.id, card.id)
                    Toast.makeText(context, "Añadida a \"${folder.title}\"", Toast.LENGTH_SHORT).show()
                    onChanged()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Muestra las cartas de una lista; desmarcar una y guardar la quita de la lista. */
    fun showFolderContents(context: Context, folder: CardFolder, onChanged: () -> Unit = {}) {
        val cards = MockRepository.getCardsInFolder(folder.id)
        val builder = MaterialAlertDialogBuilder(context).setTitle(folder.title)

        if (cards.isEmpty()) {
            builder
                .setMessage("Este álbum está vacío.\nMantén pulsada una carta (en Buscar o en Colección) para añadirla.")
                .setPositiveButton("Cerrar", null)
                .show()
            return
        }

        val labels = cards.map { "${it.name} · ${it.expansion}" }.toTypedArray()
        val checked = BooleanArray(cards.size) { true }

        builder
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Guardar") { _, _ ->
                var removed = 0
                cards.forEachIndexed { i, card ->
                    if (!checked[i] && MockRepository.removeCardFromFolder(folder.id, card.id)) removed++
                }
                if (removed > 0) {
                    Toast.makeText(context, "Quitadas $removed carta(s) de la lista", Toast.LENGTH_SHORT).show()
                    onChanged()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun promptNewList(context: Context, onCreated: (CardFolder) -> Unit) {
        val input = EditText(context).apply {
            hint = "Nombre de la lista"
            setSingleLine()
        }
        val density = context.resources.displayMetrics.density
        val container = FrameLayout(context).apply {
            val side = (20 * density).toInt()
            setPadding(side, (8 * density).toInt(), side, 0)
            addView(input)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Nueva lista")
            .setView(container)
            .setPositiveButton("Crear") { _, _ ->
                val title = input.text?.toString()?.trim().orEmpty()
                if (title.isNotBlank()) onCreated(MockRepository.addFolder(title, ""))
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
