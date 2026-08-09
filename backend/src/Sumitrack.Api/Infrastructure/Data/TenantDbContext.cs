using Microsoft.EntityFrameworkCore;
using Sumitrack.Api.Models.Entities;

namespace Sumitrack.Api.Infrastructure.Data;

public class TenantDbContext : DbContext
{
    public TenantDbContext(DbContextOptions<TenantDbContext> options) : base(options) { }

    public DbSet<User> Users => Set<User>();
    public DbSet<Setting> Settings => Set<Setting>();
    public DbSet<Client> Clients => Set<Client>();
    public DbSet<Product> Products => Set<Product>();
    public DbSet<ProductVariant> ProductVariants => Set<ProductVariant>();
    public DbSet<Sale> Sales => Set<Sale>();
    public DbSet<SaleItem> SaleItems => Set<SaleItem>();
    public DbSet<Installment> Installments => Set<Installment>();
    public DbSet<Payment> Payments => Set<Payment>();
    public DbSet<CreditBalance> CreditBalances => Set<CreditBalance>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<User>(entity =>
        {
            entity.ToTable("users");
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Id)
                .HasColumnName("id")
                .HasColumnType("uuid");
            entity.Property(e => e.Username)
                .HasColumnName("username")
                .HasColumnType("character varying(100)")
                .HasMaxLength(100)
                .IsRequired();
            entity.HasIndex(e => e.Username)
                .HasDatabaseName("ix_users_username")
                .IsUnique();
            entity.Property(e => e.PasswordHash)
                .HasColumnName("password_hash")
                .HasColumnType("character varying(255)")
                .HasMaxLength(255)
                .IsRequired();
            entity.Property(e => e.TenantId)
                .HasColumnName("tenant_id")
                .HasColumnType("uuid")
                .IsRequired();
            entity.Property(e => e.CreatedAt)
                .HasColumnName("created_at")
                .HasColumnType("timestamp with time zone")
                .HasDefaultValueSql("NOW()");
            entity.Property(e => e.UpdatedAt)
                .HasColumnName("updated_at")
                .HasColumnType("timestamp with time zone")
                .HasDefaultValueSql("NOW()");
        });

        modelBuilder.Entity<Setting>(entity =>
        {
            entity.ToTable("settings");
            entity.HasKey(e => e.Key);
            entity.Property(e => e.Key)
                .HasColumnName("key")
                .HasColumnType("character varying(100)")
                .HasMaxLength(100)
                .IsRequired();
            entity.Property(e => e.Value)
                .HasColumnName("value")
                .HasColumnType("text");
            entity.Property(e => e.CreatedAt)
                .HasColumnName("created_at")
                .HasColumnType("timestamp with time zone")
                .HasDefaultValueSql("NOW()");
            entity.Property(e => e.UpdatedAt)
                .HasColumnName("updated_at")
                .HasColumnType("timestamp with time zone")
                .HasDefaultValueSql("NOW()");
            entity.Property(e => e.SyncStatus)
                .HasColumnName("sync_status")
                .HasColumnType("character varying(20)")
                .HasDefaultValue("synced");
        });

        modelBuilder.Entity<Client>(entity =>
        {
            entity.ToTable("clients");
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Id).HasColumnName("id").HasColumnType("uuid");
            entity.Property(e => e.FkTenant).HasColumnName("fk_tenant").HasColumnType("uuid").IsRequired();
            entity.Property(e => e.Name).HasColumnName("name").HasColumnType("character varying(200)").HasMaxLength(200).IsRequired();
            entity.Property(e => e.Phone).HasColumnName("phone").HasColumnType("character varying(20)").HasMaxLength(20).IsRequired();
            entity.Property(e => e.Rfc).HasColumnName("rfc").HasColumnType("character varying(20)").HasMaxLength(20);
            entity.Property(e => e.Address).HasColumnName("address").HasColumnType("text");
            entity.Property(e => e.Notes).HasColumnName("notes").HasColumnType("text");
            entity.Property(e => e.CreatedAt).HasColumnName("created_at").HasColumnType("timestamp with time zone").HasDefaultValueSql("NOW()");
            entity.Property(e => e.UpdatedAt).HasColumnName("updated_at").HasColumnType("timestamp with time zone").HasDefaultValueSql("NOW()");
            entity.Property(e => e.SyncStatus).HasColumnName("sync_status").HasColumnType("character varying(20)").HasDefaultValue("synced");
        });

        modelBuilder.Entity<Product>(entity =>
        {
            entity.ToTable("products");
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Id).HasColumnName("id").HasColumnType("uuid");
            entity.Property(e => e.FkTenant).HasColumnName("fk_tenant").HasColumnType("uuid").IsRequired();
            entity.Property(e => e.Name).HasColumnName("name").HasColumnType("character varying(200)").HasMaxLength(200).IsRequired();
            entity.Property(e => e.Price).HasColumnName("price").HasColumnType("numeric(18,6)").IsRequired();
            entity.Property(e => e.TaxRate).HasColumnName("tax_rate").HasColumnType("numeric(18,6)").IsRequired();
            entity.Property(e => e.IsActive).HasColumnName("is_active").HasDefaultValue(true);
            entity.Property(e => e.CreatedAt).HasColumnName("created_at").HasColumnType("timestamp with time zone").HasDefaultValueSql("NOW()");
            entity.Property(e => e.UpdatedAt).HasColumnName("updated_at").HasColumnType("timestamp with time zone").HasDefaultValueSql("NOW()");
            entity.Property(e => e.SyncStatus).HasColumnName("sync_status").HasColumnType("character varying(20)").HasDefaultValue("synced");
        });

        modelBuilder.Entity<ProductVariant>(entity =>
        {
            entity.ToTable("product_variants");
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Id).HasColumnName("id").HasColumnType("uuid");
            entity.Property(e => e.FkTenant).HasColumnName("fk_tenant").HasColumnType("uuid").IsRequired();
            entity.Property(e => e.FkProduct).HasColumnName("fk_product").HasColumnType("uuid").IsRequired();
            entity.Property(e => e.Name).HasColumnName("name").HasColumnType("character varying(100)").HasMaxLength(100).IsRequired();
            entity.Property(e => e.CreatedAt).HasColumnName("created_at").HasColumnType("timestamp with time zone").HasDefaultValueSql("NOW()");
            entity.Property(e => e.UpdatedAt).HasColumnName("updated_at").HasColumnType("timestamp with time zone").HasDefaultValueSql("NOW()");
            entity.Property(e => e.SyncStatus).HasColumnName("sync_status").HasColumnType("character varying(20)").HasDefaultValue("synced");
        });

        modelBuilder.Entity<Sale>(entity =>
        {
            entity.ToTable("sales");
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Id).HasColumnName("id").HasColumnType("uuid");
            entity.Property(e => e.FkTenant).HasColumnName("fk_tenant").HasColumnType("uuid").IsRequired();
            entity.Property(e => e.FkClient).HasColumnName("fk_client").HasColumnType("uuid").IsRequired();
            entity.Property(e => e.Folio).HasColumnName("folio").HasColumnType("character varying(50)").HasMaxLength(50).IsRequired();
            entity.Property(e => e.Total).HasColumnName("total").HasColumnType("numeric(18,6)").IsRequired();
            entity.Property(e => e.Subtotal).HasColumnName("subtotal").HasColumnType("numeric(18,6)").HasDefaultValue(0m);
            entity.Property(e => e.Tax).HasColumnName("tax").HasColumnType("numeric(18,6)").HasDefaultValue(0m);
            entity.Property(e => e.Status).HasColumnName("status").HasColumnType("character varying(20)").HasDefaultValue("pending");
            entity.Property(e => e.CreatedAt).HasColumnName("created_at").HasColumnType("timestamp with time zone").HasDefaultValueSql("NOW()");
            entity.Property(e => e.UpdatedAt).HasColumnName("updated_at").HasColumnType("timestamp with time zone").HasDefaultValueSql("NOW()");
            entity.Property(e => e.SyncStatus).HasColumnName("sync_status").HasColumnType("character varying(20)").HasDefaultValue("synced");
        });

        modelBuilder.Entity<SaleItem>(entity =>
        {
            entity.ToTable("sale_items");
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Id).HasColumnName("id").HasColumnType("uuid");
            entity.Property(e => e.FkTenant).HasColumnName("fk_tenant").HasColumnType("uuid").IsRequired();
            entity.Property(e => e.FkSale).HasColumnName("fk_sale").HasColumnType("uuid").IsRequired();
            entity.Property(e => e.FkProduct).HasColumnName("fk_product").HasColumnType("uuid").IsRequired();
            entity.Property(e => e.FkVariant).HasColumnName("fk_variant").HasColumnType("uuid");
            entity.Property(e => e.ProductName).HasColumnName("product_name").HasColumnType("character varying(200)").HasMaxLength(200).IsRequired();
            entity.Property(e => e.VariantName).HasColumnName("variant_name").HasColumnType("character varying(100)").HasMaxLength(100);
            entity.Property(e => e.Quantity).HasColumnName("quantity").IsRequired();
            entity.Property(e => e.UnitPrice).HasColumnName("unit_price").HasColumnType("numeric(18,6)").IsRequired();
            entity.Property(e => e.TaxRate).HasColumnName("tax_rate").HasColumnType("numeric(18,6)").IsRequired();
            entity.Property(e => e.CreatedAt).HasColumnName("created_at").HasColumnType("timestamp with time zone").HasDefaultValueSql("NOW()");
            entity.Property(e => e.UpdatedAt).HasColumnName("updated_at").HasColumnType("timestamp with time zone").HasDefaultValueSql("NOW()");
            entity.Property(e => e.SyncStatus).HasColumnName("sync_status").HasColumnType("character varying(20)").HasDefaultValue("synced");
        });

        modelBuilder.Entity<Installment>(entity =>
        {
            entity.ToTable("installments");
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Id).HasColumnName("id").HasColumnType("uuid");
            entity.Property(e => e.FkTenant).HasColumnName("fk_tenant").HasColumnType("uuid").IsRequired();
            entity.Property(e => e.FkSale).HasColumnName("fk_sale").HasColumnType("uuid").IsRequired();
            entity.Property(e => e.Amount).HasColumnName("amount").HasColumnType("numeric(18,6)").IsRequired();
            entity.Property(e => e.DueDate).HasColumnName("due_date").HasColumnType("timestamp with time zone").IsRequired();
            entity.Property(e => e.Status).HasColumnName("status").HasColumnType("character varying(20)").HasDefaultValue("pending");
            entity.Property(e => e.CreatedAt).HasColumnName("created_at").HasColumnType("timestamp with time zone").HasDefaultValueSql("NOW()");
            entity.Property(e => e.UpdatedAt).HasColumnName("updated_at").HasColumnType("timestamp with time zone").HasDefaultValueSql("NOW()");
            entity.Property(e => e.SyncStatus).HasColumnName("sync_status").HasColumnType("character varying(20)").HasDefaultValue("synced");
        });

        modelBuilder.Entity<Payment>(entity =>
        {
            entity.ToTable("payments");
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Id).HasColumnName("id").HasColumnType("uuid");
            entity.Property(e => e.FkTenant).HasColumnName("fk_tenant").HasColumnType("uuid").IsRequired();
            entity.Property(e => e.FkSale).HasColumnName("fk_sale").HasColumnType("uuid").IsRequired();
            entity.Property(e => e.FkInstallment).HasColumnName("fk_installment").HasColumnType("uuid");
            entity.Property(e => e.Method).HasColumnName("method").HasColumnType("character varying(30)").HasMaxLength(30).IsRequired();
            entity.Property(e => e.Amount).HasColumnName("amount").HasColumnType("numeric(18,6)").IsRequired();
            entity.Property(e => e.PaidAt).HasColumnName("paid_at").HasColumnType("timestamp with time zone").IsRequired();
            entity.Property(e => e.CreatedAt).HasColumnName("created_at").HasColumnType("timestamp with time zone").HasDefaultValueSql("NOW()");
            entity.Property(e => e.UpdatedAt).HasColumnName("updated_at").HasColumnType("timestamp with time zone").HasDefaultValueSql("NOW()");
            entity.Property(e => e.SyncStatus).HasColumnName("sync_status").HasColumnType("character varying(20)").HasDefaultValue("synced");
        });

        modelBuilder.Entity<CreditBalance>(entity =>
        {
            entity.ToTable("credit_balances");
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Id).HasColumnName("id").HasColumnType("uuid");
            entity.Property(e => e.FkTenant).HasColumnName("fk_tenant").HasColumnType("uuid").IsRequired();
            entity.Property(e => e.FkClient).HasColumnName("fk_client").HasColumnType("uuid").IsRequired();
            entity.Property(e => e.Amount).HasColumnName("amount").HasColumnType("numeric(18,6)").IsRequired();
            entity.Property(e => e.Origin).HasColumnName("origin").HasColumnType("character varying(20)").HasDefaultValue("cancellation");
            entity.Property(e => e.FkOriginSale).HasColumnName("fk_origin_sale").HasColumnType("uuid");
            entity.Property(e => e.AppliedAt).HasColumnName("applied_at").HasColumnType("timestamp with time zone");
            entity.Property(e => e.CreatedAt).HasColumnName("created_at").HasColumnType("timestamp with time zone").HasDefaultValueSql("NOW()");
            entity.Property(e => e.UpdatedAt).HasColumnName("updated_at").HasColumnType("timestamp with time zone").HasDefaultValueSql("NOW()");
            entity.Property(e => e.SyncStatus).HasColumnName("sync_status").HasColumnType("character varying(20)").HasDefaultValue("synced");
        });
    }
}
