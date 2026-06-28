using Microsoft.EntityFrameworkCore;
using Sumitrack.Api.Models.Entities;

namespace Sumitrack.Api.Infrastructure.Data;

public class AppDbContext : DbContext
{
    public AppDbContext(DbContextOptions<AppDbContext> options) : base(options) { }

    public DbSet<Tenant> Tenants => Set<Tenant>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.HasDefaultSchema("public");

        modelBuilder.Entity<Tenant>(entity =>
        {
            entity.ToTable("tenants", "public");
            entity.HasKey(e => e.Id);
            entity.Property(e => e.Id)
                .HasColumnName("id")
                .HasColumnType("uuid")
                .HasDefaultValueSql("gen_random_uuid()");
            entity.Property(e => e.Slug)
                .HasColumnName("slug")
                .HasColumnType("character varying(50)")
                .HasMaxLength(50)
                .IsRequired();
            entity.HasIndex(e => e.Slug)
                .HasDatabaseName("ix_tenants_slug")
                .IsUnique();
            entity.Property(e => e.SchemaName)
                .HasColumnName("schema_name")
                .HasColumnType("character varying(63)")
                .HasMaxLength(63)
                .IsRequired();
            entity.HasIndex(e => e.SchemaName)
                .HasDatabaseName("ix_tenants_schema_name")
                .IsUnique();
            entity.Property(e => e.CreatedAt)
                .HasColumnName("created_at")
                .HasColumnType("timestamp with time zone")
                .HasDefaultValueSql("NOW()");
        });
    }
}
