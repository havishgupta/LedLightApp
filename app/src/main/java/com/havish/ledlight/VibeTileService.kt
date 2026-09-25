package com.havish.ledlight

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class VibeTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, BleAudioService::class.java).apply {
            action = BleAudioService.ACTION_TOGGLE_VIBE
        }
        startService(intent)
        
        // Optimistic update
        val isVibing = BleAudioService.controller?.isVibing?.value == true
        qsTile.state = if (!isVibing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile.updateTile()
    }

    private fun updateTileState() {
        val isVibing = BleAudioService.controller?.isVibing?.value == true
        qsTile.state = if (isVibing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile.updateTile()
    }
}
