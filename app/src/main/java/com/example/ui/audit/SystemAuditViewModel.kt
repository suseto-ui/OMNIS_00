package com.example.ui.audit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monitoring.ProductionAuditManager
import com.example.monitoring.ProductionAuditReport
import com.example.ui.user.BadgeTriggerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SystemAuditViewModel : ViewModel() {
    private val _isRunningAudit = MutableStateFlow(false)
    val isRunningAudit = _isRunningAudit.asStateFlow()

    private val _auditReport = MutableStateFlow<ProductionAuditReport?>(null)
    val auditReport = _auditReport.asStateFlow()

    val selectedCategoryFilter = MutableStateFlow<String?>("ALL")
    val activeSubTab = MutableStateFlow(0)

    fun runAudit(context: Context) {
        viewModelScope.launch {
            _isRunningAudit.value = true
            val report = ProductionAuditManager.runComprehensiveAudit(context)
            _auditReport.value = report
            _isRunningAudit.value = false
            
            BadgeTriggerManager.triggerBadge(
                badgeId = "nis2_certifier",
                triggerVector = "PRODUCTION_COMPREHENSIVE_AUDIT_VERIFIED"
            )
        }
    }

    fun setCategoryFilter(category: String?) {
        selectedCategoryFilter.value = category
    }

    fun setActiveSubTab(tab: Int) {
        activeSubTab.value = tab
    }
}
