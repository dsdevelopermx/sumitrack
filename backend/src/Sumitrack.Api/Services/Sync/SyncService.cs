using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using Sumitrack.Api.Infrastructure.Auth;
using Sumitrack.Api.Infrastructure.Data;
using Sumitrack.Api.Models.Entities;
using Sumitrack.Api.Models.Requests;
using Sumitrack.Api.Models.Responses;

namespace Sumitrack.Api.Services.Sync;

public class SyncService : ISyncService
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    private readonly TenantDbContext _ctx;
    private readonly ITenantContext _tenantContext;

    public SyncService(TenantDbContext ctx, ITenantContext tenantContext)
    {
        _ctx = ctx;
        _tenantContext = tenantContext;
    }

    public async Task<object?> PushAsync(string entity, JsonElement payload, CancellationToken cancellationToken = default)
    {
        return entity switch
        {
            "clientes" => await PushClientesAsync(payload, cancellationToken),
            "productos" => await PushProductosAsync(payload, cancellationToken),
            "variantes" => await PushVariantesAsync(payload, cancellationToken),
            "ventas" => await PushVentasAsync(payload, cancellationToken),
            "items_venta" => await PushItemsVentaAsync(payload, cancellationToken),
            "parcialidades" => await PushParcialidadesAsync(payload, cancellationToken),
            "cobros" => await PushCobrosAsync(payload, cancellationToken),
            "creditos_a_favor" => await PushCreditosAFavorAsync(payload, cancellationToken),
            "settings" => await PushSettingsAsync(payload, cancellationToken),
            _ => null,
        };
    }

    public Task<int> GetFolioCountAsync(CancellationToken cancellationToken = default) =>
        _ctx.Sales.CountAsync(cancellationToken);

    public async Task<object?> PullAsync(string entity, DateTime? since, CancellationToken cancellationToken = default)
    {
        return entity switch
        {
            "clientes" => await PullClientesAsync(since, cancellationToken),
            "productos" => await PullProductosAsync(since, cancellationToken),
            "variantes" => await PullVariantesAsync(since, cancellationToken),
            "ventas" => await PullVentasAsync(since, cancellationToken),
            "items_venta" => await PullItemsVentaAsync(since, cancellationToken),
            "parcialidades" => await PullParcialidadesAsync(since, cancellationToken),
            "cobros" => await PullCobrosAsync(since, cancellationToken),
            "creditos_a_favor" => await PullCreditosAFavorAsync(since, cancellationToken),
            "settings" => await PullSettingsAsync(since, cancellationToken),
            _ => null,
        };
    }

    // El tenant siempre viene del contexto resuelto por TenantResolverMiddleware a partir del JWT
    // — nunca del payload del cliente, que podría traer cualquier fk_tenant sin validar.
    private Guid ResolveTenantId() => _tenantContext.TenantId!.Value;

    private static DateTime NormalizeUtc(DateTime value) =>
        value.Kind == DateTimeKind.Unspecified ? DateTime.SpecifyKind(value, DateTimeKind.Utc) : value.ToUniversalTime();

    private async Task<List<PushSyncResponseItem>> PushClientesAsync(JsonElement payload, CancellationToken ct)
    {
        var tenantId = ResolveTenantId();
        List<ClientSyncRequest> items;
        try
        {
            items = payload.Deserialize<List<ClientSyncRequest>>(JsonOptions) ?? [];
        }
        catch (JsonException ex)
        {
            return [new PushSyncResponseItem { Success = false, Error = $"Payload inválido: {ex.Message}" }];
        }

        var results = new List<PushSyncResponseItem>();

        foreach (var item in items)
        {
            var error = ValidateClient(item);
            if (error != null)
            {
                results.Add(new PushSyncResponseItem { Id = item.Id, Success = false, Error = error });
                continue;
            }

            var existing = await _ctx.Clients.FindAsync([item.Id], ct);
            if (existing is not null && existing.UpdatedAt > NormalizeUtc(item.UpdatedAt))
            {
                results.Add(new PushSyncResponseItem
                {
                    Id = item.Id,
                    Success = false,
                    Conflict = true,
                    ServerSnapshot = JsonSerializer.Serialize(existing, JsonOptions),
                });
                continue;
            }
            if (existing is null)
            {
                _ctx.Clients.Add(new Client
                {
                    Id = item.Id,
                    FkTenant = tenantId,
                    Name = item.Name,
                    Phone = item.Phone,
                    Rfc = item.Rfc,
                    Address = item.Address,
                    Notes = item.Notes,
                    CreatedAt = NormalizeUtc(item.CreatedAt),
                    UpdatedAt = NormalizeUtc(item.UpdatedAt),
                    SyncStatus = "synced",
                });
            }
            else
            {
                existing.Name = item.Name;
                existing.Phone = item.Phone;
                existing.Rfc = item.Rfc;
                existing.Address = item.Address;
                existing.Notes = item.Notes;
                existing.UpdatedAt = NormalizeUtc(item.UpdatedAt);
                existing.SyncStatus = "synced";
            }
            results.Add(new PushSyncResponseItem { Id = item.Id, Success = true });
        }

        await SaveAndReconcileAsync(results, ct);
        return results;
    }

    private static string? ValidateClient(ClientSyncRequest item)
    {
        if (item.Id == Guid.Empty) return "id requerido";
        if (string.IsNullOrWhiteSpace(item.Name)) return "name requerido";
        if (item.Name.Length > 200) return "name excede 200 caracteres";
        if (string.IsNullOrWhiteSpace(item.Phone)) return "phone requerido";
        if (item.Phone.Length > 20) return "phone excede 20 caracteres";
        if (item.Rfc != null && item.Rfc.Length > 20) return "rfc excede 20 caracteres";
        return null;
    }

    private async Task<List<PushSyncResponseItem>> PushProductosAsync(JsonElement payload, CancellationToken ct)
    {
        var tenantId = ResolveTenantId();
        List<ProductSyncRequest> items;
        try
        {
            items = payload.Deserialize<List<ProductSyncRequest>>(JsonOptions) ?? [];
        }
        catch (JsonException ex)
        {
            return [new PushSyncResponseItem { Success = false, Error = $"Payload inválido: {ex.Message}" }];
        }

        var results = new List<PushSyncResponseItem>();

        foreach (var item in items)
        {
            var error = ValidateProduct(item);
            if (error != null)
            {
                results.Add(new PushSyncResponseItem { Id = item.Id, Success = false, Error = error });
                continue;
            }

            var existing = await _ctx.Products.FindAsync([item.Id], ct);
            if (existing is not null && existing.UpdatedAt > NormalizeUtc(item.UpdatedAt))
            {
                results.Add(new PushSyncResponseItem
                {
                    Id = item.Id,
                    Success = false,
                    Conflict = true,
                    ServerSnapshot = JsonSerializer.Serialize(existing, JsonOptions),
                });
                continue;
            }
            if (existing is null)
            {
                _ctx.Products.Add(new Product
                {
                    Id = item.Id,
                    FkTenant = tenantId,
                    Name = item.Name,
                    Price = item.Price,
                    TaxRate = item.TaxRate,
                    IsActive = item.IsActive,
                    CreatedAt = NormalizeUtc(item.CreatedAt),
                    UpdatedAt = NormalizeUtc(item.UpdatedAt),
                    SyncStatus = "synced",
                });
            }
            else
            {
                existing.Name = item.Name;
                existing.Price = item.Price;
                existing.TaxRate = item.TaxRate;
                existing.IsActive = item.IsActive;
                existing.UpdatedAt = NormalizeUtc(item.UpdatedAt);
                existing.SyncStatus = "synced";
            }
            results.Add(new PushSyncResponseItem { Id = item.Id, Success = true });
        }

        await SaveAndReconcileAsync(results, ct);
        return results;
    }

    private static string? ValidateProduct(ProductSyncRequest item)
    {
        if (item.Id == Guid.Empty) return "id requerido";
        if (string.IsNullOrWhiteSpace(item.Name)) return "name requerido";
        if (item.Name.Length > 200) return "name excede 200 caracteres";
        if (item.Price < 0) return "price no puede ser negativo";
        if (item.TaxRate < 0) return "taxRate no puede ser negativo";
        return null;
    }

    private async Task<List<PushSyncResponseItem>> PushVariantesAsync(JsonElement payload, CancellationToken ct)
    {
        var tenantId = ResolveTenantId();
        List<ProductVariantSyncRequest> items;
        try
        {
            items = payload.Deserialize<List<ProductVariantSyncRequest>>(JsonOptions) ?? [];
        }
        catch (JsonException ex)
        {
            return [new PushSyncResponseItem { Success = false, Error = $"Payload inválido: {ex.Message}" }];
        }

        var results = new List<PushSyncResponseItem>();

        foreach (var item in items)
        {
            var error = ValidateVariant(item);
            if (error != null)
            {
                results.Add(new PushSyncResponseItem { Id = item.Id, Success = false, Error = error });
                continue;
            }

            var existing = await _ctx.ProductVariants.FindAsync([item.Id], ct);
            if (existing is not null && existing.UpdatedAt > NormalizeUtc(item.UpdatedAt))
            {
                results.Add(new PushSyncResponseItem
                {
                    Id = item.Id,
                    Success = false,
                    Conflict = true,
                    ServerSnapshot = JsonSerializer.Serialize(existing, JsonOptions),
                });
                continue;
            }
            if (existing is null)
            {
                _ctx.ProductVariants.Add(new ProductVariant
                {
                    Id = item.Id,
                    FkTenant = tenantId,
                    FkProduct = item.FkProduct,
                    Name = item.Name,
                    CreatedAt = NormalizeUtc(item.CreatedAt),
                    UpdatedAt = NormalizeUtc(item.UpdatedAt),
                    SyncStatus = "synced",
                });
            }
            else
            {
                existing.FkProduct = item.FkProduct;
                existing.Name = item.Name;
                existing.UpdatedAt = NormalizeUtc(item.UpdatedAt);
                existing.SyncStatus = "synced";
            }
            results.Add(new PushSyncResponseItem { Id = item.Id, Success = true });
        }

        await SaveAndReconcileAsync(results, ct);
        return results;
    }

    private static string? ValidateVariant(ProductVariantSyncRequest item)
    {
        if (item.Id == Guid.Empty) return "id requerido";
        if (item.FkProduct == Guid.Empty) return "fkProduct requerido";
        if (string.IsNullOrWhiteSpace(item.Name)) return "name requerido";
        if (item.Name.Length > 100) return "name excede 100 caracteres";
        return null;
    }

    private async Task<List<PushSyncResponseItem>> PushVentasAsync(JsonElement payload, CancellationToken ct)
    {
        var tenantId = ResolveTenantId();
        List<SaleSyncRequest> items;
        try
        {
            items = payload.Deserialize<List<SaleSyncRequest>>(JsonOptions) ?? [];
        }
        catch (JsonException ex)
        {
            return [new PushSyncResponseItem { Success = false, Error = $"Payload inválido: {ex.Message}" }];
        }

        var results = new List<PushSyncResponseItem>();

        foreach (var item in items)
        {
            var error = ValidateSale(item);
            if (error != null)
            {
                results.Add(new PushSyncResponseItem { Id = item.Id, Success = false, Error = error });
                continue;
            }

            var existing = await _ctx.Sales.FindAsync([item.Id], ct);
            if (existing is not null && existing.UpdatedAt > NormalizeUtc(item.UpdatedAt))
            {
                results.Add(new PushSyncResponseItem
                {
                    Id = item.Id,
                    Success = false,
                    Conflict = true,
                    ServerSnapshot = JsonSerializer.Serialize(existing, JsonOptions),
                });
                continue;
            }
            if (existing is null)
            {
                _ctx.Sales.Add(new Sale
                {
                    Id = item.Id,
                    FkTenant = tenantId,
                    FkClient = item.FkClient,
                    Folio = item.Folio,
                    Total = item.Total,
                    Subtotal = item.Subtotal,
                    Tax = item.Tax,
                    Status = item.Status,
                    CreatedAt = NormalizeUtc(item.CreatedAt),
                    UpdatedAt = NormalizeUtc(item.UpdatedAt),
                    SyncStatus = "synced",
                });
            }
            else
            {
                existing.FkClient = item.FkClient;
                existing.Folio = item.Folio;
                existing.Total = item.Total;
                existing.Subtotal = item.Subtotal;
                existing.Tax = item.Tax;
                existing.Status = item.Status;
                existing.UpdatedAt = NormalizeUtc(item.UpdatedAt);
                existing.SyncStatus = "synced";
            }
            results.Add(new PushSyncResponseItem { Id = item.Id, Success = true });
        }

        await SaveAndReconcileAsync(results, ct);
        return results;
    }

    private static string? ValidateSale(SaleSyncRequest item)
    {
        if (item.Id == Guid.Empty) return "id requerido";
        if (item.FkClient == Guid.Empty) return "fkClient requerido";
        if (string.IsNullOrWhiteSpace(item.Folio)) return "folio requerido";
        if (item.Folio.Length > 50) return "folio excede 50 caracteres";
        if (item.Total < 0) return "total no puede ser negativo";
        if (item.Subtotal < 0) return "subtotal no puede ser negativo";
        if (item.Tax < 0) return "tax no puede ser negativo";
        if (string.IsNullOrWhiteSpace(item.Status)) return "status requerido";
        if (item.Status.Length > 20) return "status excede 20 caracteres";
        return null;
    }

    private async Task<List<PushSyncResponseItem>> PushItemsVentaAsync(JsonElement payload, CancellationToken ct)
    {
        var tenantId = ResolveTenantId();
        List<SaleItemSyncRequest> items;
        try
        {
            items = payload.Deserialize<List<SaleItemSyncRequest>>(JsonOptions) ?? [];
        }
        catch (JsonException ex)
        {
            return [new PushSyncResponseItem { Success = false, Error = $"Payload inválido: {ex.Message}" }];
        }

        var results = new List<PushSyncResponseItem>();

        foreach (var item in items)
        {
            var error = ValidateSaleItem(item);
            if (error != null)
            {
                results.Add(new PushSyncResponseItem { Id = item.Id, Success = false, Error = error });
                continue;
            }

            var existing = await _ctx.SaleItems.FindAsync([item.Id], ct);
            if (existing is not null && existing.UpdatedAt > NormalizeUtc(item.UpdatedAt))
            {
                results.Add(new PushSyncResponseItem
                {
                    Id = item.Id,
                    Success = false,
                    Conflict = true,
                    ServerSnapshot = JsonSerializer.Serialize(existing, JsonOptions),
                });
                continue;
            }
            if (existing is null)
            {
                _ctx.SaleItems.Add(new SaleItem
                {
                    Id = item.Id,
                    FkTenant = tenantId,
                    FkSale = item.FkSale,
                    FkProduct = item.FkProduct,
                    FkVariant = item.FkVariant,
                    ProductName = item.ProductName,
                    VariantName = item.VariantName,
                    Quantity = item.Quantity,
                    UnitPrice = item.UnitPrice,
                    TaxRate = item.TaxRate,
                    CreatedAt = NormalizeUtc(item.CreatedAt),
                    UpdatedAt = NormalizeUtc(item.UpdatedAt),
                    SyncStatus = "synced",
                });
            }
            else
            {
                existing.FkSale = item.FkSale;
                existing.FkProduct = item.FkProduct;
                existing.FkVariant = item.FkVariant;
                existing.ProductName = item.ProductName;
                existing.VariantName = item.VariantName;
                existing.Quantity = item.Quantity;
                existing.UnitPrice = item.UnitPrice;
                existing.TaxRate = item.TaxRate;
                existing.UpdatedAt = NormalizeUtc(item.UpdatedAt);
                existing.SyncStatus = "synced";
            }
            results.Add(new PushSyncResponseItem { Id = item.Id, Success = true });
        }

        await SaveAndReconcileAsync(results, ct);
        return results;
    }

    private static string? ValidateSaleItem(SaleItemSyncRequest item)
    {
        if (item.Id == Guid.Empty) return "id requerido";
        if (item.FkSale == Guid.Empty) return "fkSale requerido";
        if (item.FkProduct == Guid.Empty) return "fkProduct requerido";
        if (string.IsNullOrWhiteSpace(item.ProductName)) return "productName requerido";
        if (item.ProductName.Length > 200) return "productName excede 200 caracteres";
        if (item.VariantName != null && item.VariantName.Length > 100) return "variantName excede 100 caracteres";
        if (item.Quantity <= 0) return "quantity debe ser mayor a 0";
        if (item.UnitPrice < 0) return "unitPrice no puede ser negativo";
        if (item.TaxRate < 0) return "taxRate no puede ser negativo";
        return null;
    }

    private async Task<List<PushSyncResponseItem>> PushParcialidadesAsync(JsonElement payload, CancellationToken ct)
    {
        var tenantId = ResolveTenantId();
        List<InstallmentSyncRequest> items;
        try
        {
            items = payload.Deserialize<List<InstallmentSyncRequest>>(JsonOptions) ?? [];
        }
        catch (JsonException ex)
        {
            return [new PushSyncResponseItem { Success = false, Error = $"Payload inválido: {ex.Message}" }];
        }

        var results = new List<PushSyncResponseItem>();

        foreach (var item in items)
        {
            var error = ValidateInstallment(item);
            if (error != null)
            {
                results.Add(new PushSyncResponseItem { Id = item.Id, Success = false, Error = error });
                continue;
            }

            var existing = await _ctx.Installments.FindAsync([item.Id], ct);
            if (existing is not null && existing.UpdatedAt > NormalizeUtc(item.UpdatedAt))
            {
                results.Add(new PushSyncResponseItem
                {
                    Id = item.Id,
                    Success = false,
                    Conflict = true,
                    ServerSnapshot = JsonSerializer.Serialize(existing, JsonOptions),
                });
                continue;
            }
            if (existing is null)
            {
                _ctx.Installments.Add(new Installment
                {
                    Id = item.Id,
                    FkTenant = tenantId,
                    FkSale = item.FkSale,
                    Amount = item.Amount,
                    DueDate = NormalizeUtc(item.DueDate),
                    Status = item.Status,
                    CreatedAt = NormalizeUtc(item.CreatedAt),
                    UpdatedAt = NormalizeUtc(item.UpdatedAt),
                    SyncStatus = "synced",
                });
            }
            else
            {
                existing.FkSale = item.FkSale;
                existing.Amount = item.Amount;
                existing.DueDate = NormalizeUtc(item.DueDate);
                existing.Status = item.Status;
                existing.UpdatedAt = NormalizeUtc(item.UpdatedAt);
                existing.SyncStatus = "synced";
            }
            results.Add(new PushSyncResponseItem { Id = item.Id, Success = true });
        }

        await SaveAndReconcileAsync(results, ct);
        return results;
    }

    private static string? ValidateInstallment(InstallmentSyncRequest item)
    {
        if (item.Id == Guid.Empty) return "id requerido";
        if (item.FkSale == Guid.Empty) return "fkSale requerido";
        if (item.Amount < 0) return "amount no puede ser negativo";
        if (string.IsNullOrWhiteSpace(item.Status)) return "status requerido";
        if (item.Status.Length > 20) return "status excede 20 caracteres";
        return null;
    }

    private async Task<List<PushSyncResponseItem>> PushCobrosAsync(JsonElement payload, CancellationToken ct)
    {
        var tenantId = ResolveTenantId();
        List<PaymentSyncRequest> items;
        try
        {
            items = payload.Deserialize<List<PaymentSyncRequest>>(JsonOptions) ?? [];
        }
        catch (JsonException ex)
        {
            return [new PushSyncResponseItem { Success = false, Error = $"Payload inválido: {ex.Message}" }];
        }

        var results = new List<PushSyncResponseItem>();

        foreach (var item in items)
        {
            var error = ValidatePayment(item);
            if (error != null)
            {
                results.Add(new PushSyncResponseItem { Id = item.Id, Success = false, Error = error });
                continue;
            }

            var existing = await _ctx.Payments.FindAsync([item.Id], ct);
            if (existing is not null && existing.UpdatedAt > NormalizeUtc(item.UpdatedAt))
            {
                results.Add(new PushSyncResponseItem
                {
                    Id = item.Id,
                    Success = false,
                    Conflict = true,
                    ServerSnapshot = JsonSerializer.Serialize(existing, JsonOptions),
                });
                continue;
            }
            if (existing is null)
            {
                _ctx.Payments.Add(new Payment
                {
                    Id = item.Id,
                    FkTenant = tenantId,
                    FkSale = item.FkSale,
                    FkInstallment = item.FkInstallment,
                    Method = item.Method,
                    Amount = item.Amount,
                    PaidAt = NormalizeUtc(item.PaidAt),
                    CreatedAt = NormalizeUtc(item.CreatedAt),
                    UpdatedAt = NormalizeUtc(item.UpdatedAt),
                    SyncStatus = "synced",
                });
            }
            else
            {
                existing.FkSale = item.FkSale;
                existing.FkInstallment = item.FkInstallment;
                existing.Method = item.Method;
                existing.Amount = item.Amount;
                existing.PaidAt = NormalizeUtc(item.PaidAt);
                existing.UpdatedAt = NormalizeUtc(item.UpdatedAt);
                existing.SyncStatus = "synced";
            }
            results.Add(new PushSyncResponseItem { Id = item.Id, Success = true });
        }

        await SaveAndReconcileAsync(results, ct);
        return results;
    }

    private static string? ValidatePayment(PaymentSyncRequest item)
    {
        if (item.Id == Guid.Empty) return "id requerido";
        if (item.FkSale == Guid.Empty) return "fkSale requerido";
        if (string.IsNullOrWhiteSpace(item.Method)) return "method requerido";
        if (item.Method.Length > 30) return "method excede 30 caracteres";
        if (item.Amount < 0) return "amount no puede ser negativo";
        return null;
    }

    private async Task<List<PushSyncResponseItem>> PushCreditosAFavorAsync(JsonElement payload, CancellationToken ct)
    {
        var tenantId = ResolveTenantId();
        List<CreditBalanceSyncRequest> items;
        try
        {
            items = payload.Deserialize<List<CreditBalanceSyncRequest>>(JsonOptions) ?? [];
        }
        catch (JsonException ex)
        {
            return [new PushSyncResponseItem { Success = false, Error = $"Payload inválido: {ex.Message}" }];
        }

        var results = new List<PushSyncResponseItem>();

        foreach (var item in items)
        {
            var error = ValidateCreditBalance(item);
            if (error != null)
            {
                results.Add(new PushSyncResponseItem { Id = item.Id, Success = false, Error = error });
                continue;
            }

            var existing = await _ctx.CreditBalances.FindAsync([item.Id], ct);
            if (existing is not null && existing.UpdatedAt > NormalizeUtc(item.UpdatedAt))
            {
                results.Add(new PushSyncResponseItem
                {
                    Id = item.Id,
                    Success = false,
                    Conflict = true,
                    ServerSnapshot = JsonSerializer.Serialize(existing, JsonOptions),
                });
                continue;
            }
            if (existing is null)
            {
                _ctx.CreditBalances.Add(new CreditBalance
                {
                    Id = item.Id,
                    FkTenant = tenantId,
                    FkClient = item.FkClient,
                    Amount = item.Amount,
                    Origin = item.Origin,
                    FkOriginSale = item.FkOriginSale,
                    AppliedAt = item.AppliedAt.HasValue ? NormalizeUtc(item.AppliedAt.Value) : null,
                    CreatedAt = NormalizeUtc(item.CreatedAt),
                    UpdatedAt = NormalizeUtc(item.UpdatedAt),
                    SyncStatus = "synced",
                });
            }
            else
            {
                existing.FkClient = item.FkClient;
                existing.Amount = item.Amount;
                existing.Origin = item.Origin;
                existing.FkOriginSale = item.FkOriginSale;
                existing.AppliedAt = item.AppliedAt.HasValue ? NormalizeUtc(item.AppliedAt.Value) : null;
                existing.UpdatedAt = NormalizeUtc(item.UpdatedAt);
                existing.SyncStatus = "synced";
            }
            results.Add(new PushSyncResponseItem { Id = item.Id, Success = true });
        }

        await SaveAndReconcileAsync(results, ct);
        return results;
    }

    private static string? ValidateCreditBalance(CreditBalanceSyncRequest item)
    {
        if (item.Id == Guid.Empty) return "id requerido";
        if (item.FkClient == Guid.Empty) return "fkClient requerido";
        if (item.Amount < 0) return "amount no puede ser negativo";
        if (string.IsNullOrWhiteSpace(item.Origin)) return "origin requerido";
        if (item.Origin.Length > 20) return "origin excede 20 caracteres";
        return null;
    }

    private async Task<List<SettingSyncResponseItem>> PushSettingsAsync(JsonElement payload, CancellationToken ct)
    {
        List<SettingSyncRequest> items;
        try
        {
            items = payload.Deserialize<List<SettingSyncRequest>>(JsonOptions) ?? [];
        }
        catch (JsonException ex)
        {
            return [new SettingSyncResponseItem { Success = false, Error = $"Payload inválido: {ex.Message}" }];
        }

        var results = new List<SettingSyncResponseItem>();

        foreach (var item in items)
        {
            var error = ValidateSetting(item);
            if (error != null)
            {
                results.Add(new SettingSyncResponseItem { Key = item.Key, Success = false, Error = error });
                continue;
            }

            var existing = await _ctx.Settings.FindAsync([item.Key], ct);
            if (existing is not null && existing.UpdatedAt > NormalizeUtc(item.UpdatedAt))
            {
                results.Add(new SettingSyncResponseItem
                {
                    Key = item.Key,
                    Success = false,
                    Conflict = true,
                    ServerSnapshot = JsonSerializer.Serialize(existing, JsonOptions),
                });
                continue;
            }
            if (existing is null)
            {
                _ctx.Settings.Add(new Setting
                {
                    Key = item.Key,
                    Value = item.Value,
                    CreatedAt = NormalizeUtc(item.CreatedAt),
                    UpdatedAt = NormalizeUtc(item.UpdatedAt),
                    SyncStatus = "synced",
                });
            }
            else
            {
                existing.Value = item.Value;
                existing.UpdatedAt = NormalizeUtc(item.UpdatedAt);
                existing.SyncStatus = "synced";
            }
            results.Add(new SettingSyncResponseItem { Key = item.Key, Success = true });
        }

        try
        {
            await _ctx.SaveChangesAsync(ct);
        }
        catch (DbUpdateException ex)
        {
            foreach (var result in results.Where(r => r.Success))
            {
                result.Success = false;
                result.Error = $"No se pudo guardar el lote: {ex.Message}";
            }
        }

        return results;
    }

    private static string? ValidateSetting(SettingSyncRequest item)
    {
        if (string.IsNullOrWhiteSpace(item.Key)) return "key requerido";
        if (item.Key.Length > 100) return "key excede 100 caracteres";
        return null;
    }

    // SaveChangesAsync guarda TODO el lote en una sola transacción — si falla (violación de FK,
    // valor fuera de rango, etc.), ningún registro del lote quedó realmente persistido pese a que
    // cada uno ya se había marcado Success=true durante el foreach. Se revierte esa marca aquí para
    // que la respuesta refleje el resultado real en vez de mentir sobre "confirmación por registro".
    private async Task SaveAndReconcileAsync(List<PushSyncResponseItem> results, CancellationToken ct)
    {
        try
        {
            await _ctx.SaveChangesAsync(ct);
        }
        catch (DbUpdateException ex)
        {
            foreach (var result in results.Where(r => r.Success))
            {
                result.Success = false;
                result.Error = $"No se pudo guardar el lote: {ex.Message}";
            }
        }
    }

    // Sin filtro explícito de fk_tenant en ninguno de los Pull*Async: el aislamiento ya lo
    // garantiza TenantSchemaInterceptor vía `search_path` (schema-por-tenant físico) — mismo
    // razonamiento ya aplicado a Settings desde Historia 4.1. `since` se normaliza a UTC con el
    // mismo helper que usa el push, por si el query string llega sin sufijo de zona horaria.
    private async Task<List<ClientPullResponseItem>> PullClientesAsync(DateTime? since, CancellationToken ct)
    {
        var query = _ctx.Clients.AsQueryable();
        if (since.HasValue) query = query.Where(e => e.UpdatedAt > NormalizeUtc(since.Value));
        return await query.Select(e => new ClientPullResponseItem
        {
            Id = e.Id,
            FkTenant = e.FkTenant,
            Name = e.Name,
            Phone = e.Phone,
            Rfc = e.Rfc,
            Address = e.Address,
            Notes = e.Notes,
            CreatedAt = e.CreatedAt,
            UpdatedAt = e.UpdatedAt,
        }).ToListAsync(ct);
    }

    private async Task<List<ProductPullResponseItem>> PullProductosAsync(DateTime? since, CancellationToken ct)
    {
        var query = _ctx.Products.AsQueryable();
        if (since.HasValue) query = query.Where(e => e.UpdatedAt > NormalizeUtc(since.Value));
        return await query.Select(e => new ProductPullResponseItem
        {
            Id = e.Id,
            FkTenant = e.FkTenant,
            Name = e.Name,
            Price = e.Price,
            TaxRate = e.TaxRate,
            IsActive = e.IsActive,
            CreatedAt = e.CreatedAt,
            UpdatedAt = e.UpdatedAt,
        }).ToListAsync(ct);
    }

    private async Task<List<ProductVariantPullResponseItem>> PullVariantesAsync(DateTime? since, CancellationToken ct)
    {
        var query = _ctx.ProductVariants.AsQueryable();
        if (since.HasValue) query = query.Where(e => e.UpdatedAt > NormalizeUtc(since.Value));
        return await query.Select(e => new ProductVariantPullResponseItem
        {
            Id = e.Id,
            FkTenant = e.FkTenant,
            FkProduct = e.FkProduct,
            Name = e.Name,
            CreatedAt = e.CreatedAt,
            UpdatedAt = e.UpdatedAt,
        }).ToListAsync(ct);
    }

    private async Task<List<SalePullResponseItem>> PullVentasAsync(DateTime? since, CancellationToken ct)
    {
        var query = _ctx.Sales.AsQueryable();
        if (since.HasValue) query = query.Where(e => e.UpdatedAt > NormalizeUtc(since.Value));
        return await query.Select(e => new SalePullResponseItem
        {
            Id = e.Id,
            FkTenant = e.FkTenant,
            FkClient = e.FkClient,
            Folio = e.Folio,
            Total = e.Total,
            Subtotal = e.Subtotal,
            Tax = e.Tax,
            Status = e.Status,
            CreatedAt = e.CreatedAt,
            UpdatedAt = e.UpdatedAt,
        }).ToListAsync(ct);
    }

    private async Task<List<SaleItemPullResponseItem>> PullItemsVentaAsync(DateTime? since, CancellationToken ct)
    {
        var query = _ctx.SaleItems.AsQueryable();
        if (since.HasValue) query = query.Where(e => e.UpdatedAt > NormalizeUtc(since.Value));
        return await query.Select(e => new SaleItemPullResponseItem
        {
            Id = e.Id,
            FkTenant = e.FkTenant,
            FkSale = e.FkSale,
            FkProduct = e.FkProduct,
            FkVariant = e.FkVariant,
            ProductName = e.ProductName,
            VariantName = e.VariantName,
            Quantity = e.Quantity,
            UnitPrice = e.UnitPrice,
            TaxRate = e.TaxRate,
            CreatedAt = e.CreatedAt,
            UpdatedAt = e.UpdatedAt,
        }).ToListAsync(ct);
    }

    private async Task<List<InstallmentPullResponseItem>> PullParcialidadesAsync(DateTime? since, CancellationToken ct)
    {
        var query = _ctx.Installments.AsQueryable();
        if (since.HasValue) query = query.Where(e => e.UpdatedAt > NormalizeUtc(since.Value));
        return await query.Select(e => new InstallmentPullResponseItem
        {
            Id = e.Id,
            FkTenant = e.FkTenant,
            FkSale = e.FkSale,
            Amount = e.Amount,
            DueDate = e.DueDate,
            Status = e.Status,
            CreatedAt = e.CreatedAt,
            UpdatedAt = e.UpdatedAt,
        }).ToListAsync(ct);
    }

    private async Task<List<PaymentPullResponseItem>> PullCobrosAsync(DateTime? since, CancellationToken ct)
    {
        var query = _ctx.Payments.AsQueryable();
        if (since.HasValue) query = query.Where(e => e.UpdatedAt > NormalizeUtc(since.Value));
        return await query.Select(e => new PaymentPullResponseItem
        {
            Id = e.Id,
            FkTenant = e.FkTenant,
            FkSale = e.FkSale,
            FkInstallment = e.FkInstallment,
            Method = e.Method,
            Amount = e.Amount,
            PaidAt = e.PaidAt,
            CreatedAt = e.CreatedAt,
            UpdatedAt = e.UpdatedAt,
        }).ToListAsync(ct);
    }

    private async Task<List<CreditBalancePullResponseItem>> PullCreditosAFavorAsync(DateTime? since, CancellationToken ct)
    {
        var query = _ctx.CreditBalances.AsQueryable();
        if (since.HasValue) query = query.Where(e => e.UpdatedAt > NormalizeUtc(since.Value));
        return await query.Select(e => new CreditBalancePullResponseItem
        {
            Id = e.Id,
            FkTenant = e.FkTenant,
            FkClient = e.FkClient,
            Amount = e.Amount,
            Origin = e.Origin,
            FkOriginSale = e.FkOriginSale,
            AppliedAt = e.AppliedAt,
            CreatedAt = e.CreatedAt,
            UpdatedAt = e.UpdatedAt,
        }).ToListAsync(ct);
    }

    private async Task<List<SettingPullResponseItem>> PullSettingsAsync(DateTime? since, CancellationToken ct)
    {
        var query = _ctx.Settings.AsQueryable();
        if (since.HasValue) query = query.Where(e => e.UpdatedAt > NormalizeUtc(since.Value));
        return await query.Select(e => new SettingPullResponseItem
        {
            Key = e.Key,
            Value = e.Value,
            CreatedAt = e.CreatedAt,
            UpdatedAt = e.UpdatedAt,
        }).ToListAsync(ct);
    }
}
