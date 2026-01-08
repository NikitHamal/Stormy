package com.codex.stormy.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.codex.stormy.utils.AppError
import com.codex.stormy.utils.CircuitBreaker
import com.codex.stormy.utils.CircuitOpenException
import com.codex.stormy.utils.CompositeRateLimiter
import com.codex.stormy.utils.Constants
import com.codex.stormy.utils.RateLimiter
import com.codex.stormy.utils.RetryPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Centralized network manager with resilience patterns.
 *
 * Features:
 * - Circuit breaker for fail-fast behavior
 * - Rate limiting to prevent API abuse
 * - Automatic retry with exponential backoff
 * - Connection pooling and keep-alive
 * - Network state monitoring
 * - Comprehensive metrics
 *
 * Usage:
 * ```
 * val result = networkManager.executeWithResilience("ai-request") {
 *     // Your network call here
 * }
 * ```
 */
class NetworkManager private constructor(
    private val context: Context
) {
    companion object {
        @Volatile
        private var instance: NetworkManager? = null

        fun getInstance(context: Context): NetworkManager {
            return instance ?: synchronized(this) {
                instance ?: NetworkManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // Network state
    private val _networkState = MutableStateFlow<NetworkState>(NetworkState.Unknown)
    val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    // Circuit breakers for different services
    private val circuitBreakers = mutableMapOf<String, CircuitBreaker>()

    // Rate limiters
    private val aiRateLimiter = CompositeRateLimiter.withMinuteAndHourLimits(
        perMinute = Constants.Ai.REQUESTS_PER_MINUTE_LIMIT,
        perHour = Constants.Ai.REQUESTS_PER_HOUR_LIMIT
    )

    private val generalRateLimiter = RateLimiter.perMinute(120)

    // Retry policies
    private val aiRetryPolicy = RetryPolicy.forAi()
    private val networkRetryPolicy = RetryPolicy.forNetwork()

    // Metrics
    private val totalRequests = AtomicLong(0)
    private val successfulRequests = AtomicLong(0)
    private val failedRequests = AtomicLong(0)
    private val retriedRequests = AtomicLong(0)
    private val rateLimitedRequests = AtomicLong(0)
    private val circuitBreakerRejections = AtomicLong(0)

    // Shared OkHttpClient with optimized settings
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(Constants.Timeout.AI_CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
            .readTimeout(Constants.Timeout.NETWORK_READ_TIMEOUT, TimeUnit.MILLISECONDS)
            .writeTimeout(Constants.Timeout.NETWORK_WRITE_TIMEOUT, TimeUnit.MILLISECONDS)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // Streaming client with longer timeouts
    val streamingClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .readTimeout(Constants.Timeout.AI_STREAM_TIMEOUT, TimeUnit.MILLISECONDS)
            .build()
    }

    init {
        setupNetworkCallback()
        checkInitialNetworkState()
    }

    /**
     * Execute a network call with all resilience patterns applied.
     *
     * @param serviceId Identifier for the service (used for circuit breaker isolation)
     * @param isAiRequest Whether this is an AI API request (applies AI-specific limits)
     * @param block The network operation to execute
     * @return Result of the operation
     */
    suspend fun <T> executeWithResilience(
        serviceId: String = "default",
        isAiRequest: Boolean = false,
        block: suspend () -> T
    ): T {
        totalRequests.incrementAndGet()

        // Check network state
        if (!_isOnline.value) {
            failedRequests.incrementAndGet()
            throw AppError.NetworkError.NoInternetConnection()
        }

        // Check rate limits
        val rateLimiter = if (isAiRequest) aiRateLimiter else generalRateLimiter
        if (rateLimiter.isRateLimited) {
            rateLimitedRequests.incrementAndGet()
            if (!rateLimiter.acquire(Constants.Ai.MIN_REQUEST_INTERVAL_MS * 10)) {
                failedRequests.incrementAndGet()
                throw AppError.NetworkError.RateLimited()
            }
        }

        // Get or create circuit breaker
        val circuitBreaker = getOrCreateCircuitBreaker(serviceId, isAiRequest)

        // Execute with circuit breaker
        return try {
            circuitBreaker.execute {
                // Apply retry policy
                val retryPolicy = if (isAiRequest) aiRetryPolicy else networkRetryPolicy
                retryPolicy.execute {
                    block()
                }
            }.also {
                successfulRequests.incrementAndGet()
            }
        } catch (e: CircuitOpenException) {
            circuitBreakerRejections.incrementAndGet()
            failedRequests.incrementAndGet()
            val remaining = circuitBreaker.getRemainingOpenTime()
            throw AppError.AiError.CircuitOpen(remaining)
        } catch (e: SocketTimeoutException) {
            failedRequests.incrementAndGet()
            throw AppError.NetworkError.ConnectionTimeout(
                Constants.Timeout.NETWORK_TIMEOUT,
                e
            )
        } catch (e: IOException) {
            failedRequests.incrementAndGet()
            throw AppError.NetworkError.NoInternetConnection(e)
        } catch (e: AppError) {
            failedRequests.incrementAndGet()
            throw e
        } catch (e: Exception) {
            failedRequests.incrementAndGet()
            throw e
        }
    }

    /**
     * Execute an AI request with appropriate resilience patterns.
     */
    suspend fun <T> executeAiRequest(
        serviceId: String = "ai",
        block: suspend () -> T
    ): T = executeWithResilience(serviceId, isAiRequest = true, block)

    /**
     * Get or create a circuit breaker for a service.
     */
    private fun getOrCreateCircuitBreaker(serviceId: String, isAiRequest: Boolean): CircuitBreaker {
        return circuitBreakers.getOrPut(serviceId) {
            if (isAiRequest) {
                CircuitBreaker.forApi(serviceId)
            } else {
                CircuitBreaker(
                    failureThreshold = 5,
                    successThreshold = 2,
                    openDurationMs = 30_000L,
                    name = serviceId
                )
            }
        }
    }

    /**
     * Reset a specific circuit breaker.
     */
    suspend fun resetCircuitBreaker(serviceId: String) {
        circuitBreakers[serviceId]?.reset()
    }

    /**
     * Reset all circuit breakers.
     */
    suspend fun resetAllCircuitBreakers() {
        circuitBreakers.values.forEach { it.reset() }
    }

    /**
     * Get the current state of a circuit breaker.
     */
    fun getCircuitBreakerState(serviceId: String): CircuitBreaker.State? {
        return circuitBreakers[serviceId]?.currentState
    }

    /**
     * Check if a service is available (circuit not open).
     */
    fun isServiceAvailable(serviceId: String): Boolean {
        return circuitBreakers[serviceId]?.isCallPermitted() ?: true
    }

    /**
     * Setup network connectivity monitoring.
     */
    private fun setupNetworkCallback() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.value = true
                updateNetworkState(connectivityManager.getNetworkCapabilities(network))
            }

            override fun onLost(network: Network) {
                _isOnline.value = false
                _networkState.value = NetworkState.Disconnected
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                updateNetworkState(capabilities)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    /**
     * Check initial network state.
     */
    private fun checkInitialNetworkState() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }

        _isOnline.value = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        updateNetworkState(capabilities)
    }

    /**
     * Update network state based on capabilities.
     */
    private fun updateNetworkState(capabilities: NetworkCapabilities?) {
        _networkState.value = when {
            capabilities == null -> NetworkState.Disconnected
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                val speed = estimateSpeed(capabilities)
                NetworkState.Connected(ConnectionType.WIFI, speed)
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val speed = estimateSpeed(capabilities)
                NetworkState.Connected(ConnectionType.CELLULAR, speed)
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                NetworkState.Connected(ConnectionType.ETHERNET, ConnectionSpeed.HIGH)
            }
            else -> NetworkState.Connected(ConnectionType.OTHER, ConnectionSpeed.UNKNOWN)
        }
    }

    /**
     * Estimate connection speed from capabilities.
     */
    private fun estimateSpeed(capabilities: NetworkCapabilities): ConnectionSpeed {
        val downstreamBandwidth = capabilities.linkDownstreamBandwidthKbps

        return when {
            downstreamBandwidth >= 25000 -> ConnectionSpeed.HIGH // 25+ Mbps
            downstreamBandwidth >= 5000 -> ConnectionSpeed.MEDIUM // 5-25 Mbps
            downstreamBandwidth >= 1000 -> ConnectionSpeed.LOW // 1-5 Mbps
            downstreamBandwidth > 0 -> ConnectionSpeed.VERY_LOW // < 1 Mbps
            else -> ConnectionSpeed.UNKNOWN
        }
    }

    /**
     * Get network metrics.
     */
    fun getMetrics(): NetworkMetrics = NetworkMetrics(
        totalRequests = totalRequests.get(),
        successfulRequests = successfulRequests.get(),
        failedRequests = failedRequests.get(),
        retriedRequests = retriedRequests.get(),
        rateLimitedRequests = rateLimitedRequests.get(),
        circuitBreakerRejections = circuitBreakerRejections.get(),
        isOnline = _isOnline.value,
        networkState = _networkState.value,
        circuitBreakerStates = circuitBreakers.mapValues { it.value.currentState }
    )

    /**
     * Reset all metrics.
     */
    fun resetMetrics() {
        totalRequests.set(0)
        successfulRequests.set(0)
        failedRequests.set(0)
        retriedRequests.set(0)
        rateLimitedRequests.set(0)
        circuitBreakerRejections.set(0)
    }
}

/**
 * Network connection state.
 */
sealed class NetworkState {
    data object Unknown : NetworkState()
    data object Disconnected : NetworkState()
    data class Connected(
        val type: ConnectionType,
        val speed: ConnectionSpeed
    ) : NetworkState()
}

/**
 * Connection type.
 */
enum class ConnectionType {
    WIFI,
    CELLULAR,
    ETHERNET,
    OTHER
}

/**
 * Connection speed estimate.
 */
enum class ConnectionSpeed {
    UNKNOWN,
    VERY_LOW,
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Network manager metrics.
 */
data class NetworkMetrics(
    val totalRequests: Long,
    val successfulRequests: Long,
    val failedRequests: Long,
    val retriedRequests: Long,
    val rateLimitedRequests: Long,
    val circuitBreakerRejections: Long,
    val isOnline: Boolean,
    val networkState: NetworkState,
    val circuitBreakerStates: Map<String, CircuitBreaker.State>
) {
    val successRate: Double
        get() = if (totalRequests > 0) successfulRequests.toDouble() / totalRequests else 0.0

    val failureRate: Double
        get() = if (totalRequests > 0) failedRequests.toDouble() / totalRequests else 0.0

    val retryRate: Double
        get() = if (totalRequests > 0) retriedRequests.toDouble() / totalRequests else 0.0
}
