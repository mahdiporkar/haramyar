package ir.haramyar.rozeh.application.query;

import java.util.List;

/**
 * Public description of what can currently be requested and which provisional policies apply.
 *
 * @param hostCancellationNoticeMinutes minimum notice for host cancellations, or -1 when no deadline is configured
 */
public record ServiceCatalog(List<String> categories, List<String> audiences, List<String> deliveryModes, List<String> confirmationBy,
    boolean providerRequiredForConfirmation, long hostCancellationNoticeMinutes, boolean requireVerifiedMobile, boolean provisional) {}
