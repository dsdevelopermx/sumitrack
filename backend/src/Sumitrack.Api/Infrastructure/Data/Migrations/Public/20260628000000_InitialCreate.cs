using System;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Infrastructure;
using Microsoft.EntityFrameworkCore.Migrations;
using Sumitrack.Api.Infrastructure.Data;

#nullable disable

namespace Sumitrack.Api.Infrastructure.Data.Migrations.Public;

[DbContext(typeof(AppDbContext))]
[Migration("20260628000000_InitialCreate")]
public partial class InitialCreate : Migration
{
    /// <inheritdoc />
    protected override void Up(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.EnsureSchema(name: "public");

        migrationBuilder.CreateTable(
            name: "tenants",
            schema: "public",
            columns: table => new
            {
                id = table.Column<Guid>(type: "uuid", nullable: false, defaultValueSql: "gen_random_uuid()"),
                slug = table.Column<string>(type: "character varying(50)", maxLength: 50, nullable: false),
                schema_name = table.Column<string>(type: "character varying(63)", maxLength: 63, nullable: false),
                created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false, defaultValueSql: "NOW()")
            },
            constraints: table =>
            {
                table.PrimaryKey("pk_tenants", x => x.id);
            });

        migrationBuilder.CreateIndex(
            name: "ix_tenants_schema_name",
            schema: "public",
            table: "tenants",
            column: "schema_name",
            unique: true);

        migrationBuilder.CreateIndex(
            name: "ix_tenants_slug",
            schema: "public",
            table: "tenants",
            column: "slug",
            unique: true);
    }

    /// <inheritdoc />
    protected override void Down(MigrationBuilder migrationBuilder)
    {
        migrationBuilder.DropTable(name: "tenants", schema: "public");
    }
}
